package io.konveyor.tackle.core.internal.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents additional query information to inspect annotations in annotated symbols.
 */
public class AnnotationQuery {

    /**
     * The annotation type, ie: <code>@org.business.BeanAnnotation</code>
     */
    private String type;

    /**
     * Indicates whether this AnnotationQuery is done on an annotation (location == ANNOTATION)
     */
    private boolean isOnAnnotation;

    /**
     * The elements within the annotation, ie, "value" in <code>@BeanAnnotation(value = "value")</code>
     */
    private Map<String, String> elements;

    public AnnotationQuery(String type, Map<String, String> elements, boolean isOnAnnotation) {
        this.type = type;
        this.elements = elements;
        this.isOnAnnotation = isOnAnnotation;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getElements() {
        return elements;
    }

    public boolean isOnAnnotation() {
        return this.isOnAnnotation;
    }

    /**
     * Checks whether the query matches against a given annotation
     */
    public boolean matchesAnnotation(String annotation) {
        // If the annotation query is happening on an annotation, the annotation field in the annotation query can be null
        if (isOnAnnotation() && getType() == null) {
            return true;
        // Classes in the "java.lang" package are never imported, so there is a chance these annotations don't come
        // as FQNs from the LS. Therefore lets check if the annotation is in "java.lang"
        } else if (getType().startsWith("java.lang.") && !annotation.contains(".")) {
            return Pattern.matches(getType().replace("java.lang.", ""), annotation);
        } else {
            return Pattern.matches(getType(), annotation);
        }
    }

    public static AnnotationQuery fromMap(String query, Map<String, Object> annotationQuery, int location) {
        if (annotationQuery == null) {
            return null;
        }

        boolean isOnAnnotation = location == 4;
        String typePattern = isOnAnnotation && annotationQuery.get("pattern").equals("") ? query : (String) annotationQuery.get("pattern");;
        final Map<String, String> elements = new HashMap<>();
        List<Map<String, String>> mapElements = (List<Map<String, String>>) annotationQuery.get("elements");
        for (int i = 0; mapElements != null && i < mapElements.size(); i++) {
            String key = mapElements.get(i).get("name");
            String value = mapElements.get(i).get("value");
            elements.put(key, value);
        }

        return new AnnotationQuery(typePattern, elements, isOnAnnotation);
    }
}
