package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses a synthetic DTO's labeled toString to bind fields, never field order or obfuscated names. */
public final class ArtworkModelAccess {
    public final Class<?> cardClass;
    public final Class<?> modelClass;
    public final boolean splitModel;
    private final Field cardModel;
    private final Field info;
    private final Field playerId;
    private final Field packageName;
    private final Field title;
    private final Field artist;
    private final Class<?> immersiveCardClass;
    private final Field immersiveCard;

    public ArtworkModelAccess(Class<?> cardClass, Class<?> modelClass, Class<?> infoClass)
            throws ReflectiveOperationException {
        this(cardClass, modelClass, infoClass, null);
    }

    public ArtworkModelAccess(Class<?> cardClass, Class<?> modelClass, Class<?> infoClass,
            Class<?> immersiveCardClass) throws ReflectiveOperationException {
        this.cardClass = cardClass;
        this.modelClass = modelClass;
        splitModel = modelClass != infoClass;
        cardModel = uniqueField(cardClass, modelClass);
        this.immersiveCardClass = immersiveCardClass;
        immersiveCard = immersiveCardClass == null ? null : uniqueField(immersiveCardClass, cardClass);
        info = splitModel ? uniqueField(modelClass, infoClass) : null;
        Map<String, Field> labels = bindIdentityFields(infoClass,
                splitModel ? "MediaInfo(uniqueId=" : "MediaModel(uniqueId=");
        playerId = labels.get("uniqueId");
        packageName = labels.get("pkg");
        title = labels.get("songName");
        artist = labels.get("artist");
    }

    public ArtworkCardIdentity readCard(Object card) throws ReflectiveOperationException {
        if (card != null && card.getClass() == immersiveCardClass) card = immersiveCard.get(card);
        if (card == null || card.getClass() != cardClass) return null;
        Object model = cardModel.get(card);
        if (model == null || model.getClass() != modelClass) return null;
        Object identity = info == null ? model : info.get(model);
        if (identity == null) return null;
        return new ArtworkCardIdentity((String) playerId.get(identity), (String) packageName.get(identity),
                (String) title.get(identity), (String) artist.get(identity));
    }

    public boolean isCard(Object value) {
        return value != null && (value.getClass() == cardClass || value.getClass() == immersiveCardClass);
    }

    public Class<?> modulePayloadType(ArtworkPlaybackPolicy.Surface surface) {
        return surface == ArtworkPlaybackPolicy.Surface.IMMERSIVE ? immersiveCardClass : cardClass;
    }

    public static Map<String, Field> bindIdentityFields(Class<?> type, String marker)
            throws ReflectiveOperationException {
        if (!Modifier.isFinal(type.getModifiers())) throw new IllegalArgumentException("model_not_final");
        List<Constructor<?>> candidates = new ArrayList<>();
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            if (!candidate.isSynthetic() && candidate.getParameterCount() >= 4
                    && candidate.getParameterCount() <= 24
                    && candidate.getParameterTypes()[0] == String.class
                    && candidate.getParameterTypes()[1] == String.class) candidates.add(candidate);
        }
        if (candidates.size() != 1) throw new IllegalArgumentException("model_constructor_ambiguous");
        Constructor<?> constructor = candidates.get(0);
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int index = 0; index < types.length; index++) {
            Class<?> parameter = types[index];
            if (parameter == String.class) arguments[index] = "__artwork_probe_" + index + "__";
            else if (parameter == boolean.class) arguments[index] = false;
            else if (parameter == int.class) arguments[index] = 0;
            else if (parameter == long.class || parameter == Long.class) arguments[index] = 1000L + index;
            else if (List.class.isAssignableFrom(parameter)) arguments[index] = new ArrayList<>();
            else if (Map.class.isAssignableFrom(parameter)) arguments[index] = new LinkedHashMap<>();
            else if (parameter.isPrimitive()) throw new IllegalArgumentException("model_primitive_unknown");
            // Nullable icon/intent/lyric DTOs remain null; a new mandatory property fails closed.
        }
        constructor.setAccessible(true);
        Object probe = constructor.newInstance(arguments);
        String text = probe.toString();
        if (!text.startsWith(marker) || text.length() > 4096) throw new IllegalArgumentException("model_marker");
        Map<String, Field> fields = new LinkedHashMap<>();
        for (String label : new String[]{"uniqueId", "songName", "artist", "pkg"}) {
            String prefix = label.equals("uniqueId") ? "uniqueId=" : ", " + label + "=";
            int offset = text.indexOf(prefix);
            if (offset < 0 || text.indexOf(prefix, offset + prefix.length()) >= 0) {
                throw new IllegalArgumentException("model_label_ambiguous");
            }
            int start = offset + prefix.length();
            int end = text.indexOf(',', start);
            if (end < 0) end = text.indexOf(')', start);
            if (end < 0) throw new IllegalArgumentException("model_label_end");
            String sentinel = text.substring(start, end);
            if (!sentinel.matches("__artwork_probe_[0-9]+__")) throw new IllegalArgumentException("model_label_value");
            Field selected = null;
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (!sentinel.equals(field.get(probe))) continue;
                if (selected != null || !Modifier.isFinal(field.getModifiers())) {
                    throw new IllegalArgumentException("model_field_ambiguous");
                }
                selected = field;
            }
            if (selected == null || fields.containsValue(selected)) throw new IllegalArgumentException("model_field_missing");
            fields.put(label, selected);
        }
        return fields;
    }

    static Field uniqueField(Class<?> owner, Class<?> type) throws ReflectiveOperationException {
        Field selected = null;
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != type) continue;
            if (selected != null) throw new NoSuchFieldException("ambiguous_typed_field");
            selected = field;
        }
        if (selected == null) throw new NoSuchFieldException("missing_typed_field");
        selected.setAccessible(true);
        return selected;
    }
}
