package io.konveyor.tackle.core.internal.symbol;

import io.konveyor.tackle.core.internal.query.AnnotationQuery;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.Annotation;
import org.eclipse.jdt.internal.core.SourceRefElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mixin interface for {@link SymbolProvider}s that allows for Annotation queries to be performed.
 */
public interface WithAnnotationQuery {
    AnnotationQuery getAnnotationQuery();
    void setAnnotationQuery(AnnotationQuery annotationQuery);

    /**
     * Checks whether a given matched symbol has the corresponding annotations, if any.
     *
     * @param match the matched symbol.
     * @param matchClasses a list of potential classes for the match to be cast to. In order to get the annotations from
     *                     the match, it must be cast to a specific type of symbol. See usage examples.
     * @return a boolean indicating whether the symbol has the specified annotations.
     */
    default boolean matchesAnnotationQuery(SearchMatch match, List<Class<? extends SourceRefElement>> matchClasses) {
        if (getAnnotationQuery() != null) {
            try {
                // Try to cast the match element into one of the given matchClasses
                IAnnotation[] annotations = matchClasses.stream()
                        .filter(c -> c.isInstance(match.getElement()))
                        .map(c -> c.cast(match.getElement()))
                        .map(this::tryToGetAnnotations).findFirst().orElse(new IAnnotation[]{});

                // If we are expecting annotations and the symbol is not annotated, return false
                if (annotations.length == 0) {
                    return false;
                }

                // Iterate over the annotation this symbol is annotated with
                for (IAnnotation annotation : annotations) {
                    // See if the annotation's name matches the pattern given in the query for the annotation
                    String fqn = getFQN(annotation);
                    if (getAnnotationQuery().matchesAnnotation(fqn)) {
                        return doElementsMatch((Annotation) annotation);
                    } else {
                        // The LS doesn't seem to be able to match on annotations within annotations, but
                        // if the main annotation doesn't match, there might be some annotations inside:
                        for (IMemberValuePair member : annotation.getMemberValuePairs()) {
                            if (member.getValueKind() == IMemberValuePair.K_ANNOTATION) {
                                if (member.getValue() instanceof Object[]) {
                                    Object[] objs = (Object[]) member.getValue();
                                    for (int i = 0; i < objs.length; i++) {
                                        Annotation innerAnnotation = (Annotation) objs[i];
                                        fqn = getFQN(innerAnnotation);
                                        if (getAnnotationQuery().matchesAnnotation(fqn)) {
                                            return doElementsMatch(innerAnnotation);
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
                return false;
            } catch(JavaModelException e) {
                // TODO: print exception
                return false;
            }
        }

        return true;
    }

    /**
     * This relatively complicated function checks whether the elements of the annotation query match
     * the elements of the actual annotation being inspected.
     *
     * Java annotations can have all sorts of elements inside: Strings, Arrays, or even other annotations:
     * <pre>
     * {@code
     * @DataSourceDefinitions({
     *         @DataSourceDefinition(
     *                 name = "jdbc/multiple-ds-xa",
     *                 className="com.example.MyDataSource",
     *                 portNumber=6689,
     *                 serverName="example.com",
     *                 user="lance",
     *                 password="secret"
     *         ),
     *         @DataSourceDefinition(
     *                 name = "jdbc/multiple-ds-non-xa",
     *                 className="com.example.MyDataSource",
     *                 portNumber=6689,
     *                 serverName="example.com",
     *                 user="lance",
     *                 password="secret",
     *                 transactional = false
     *         ),
     * })
     * public class AnnotationMultipleDs {
     * }
     *
     * </pre>
     *
     * If we have a query like this:
     * <pre>
     *   when:
     *     java.referenced:
     *       location: ANNOTATION
     *       pattern: javax.annotation.sql.DataSourceDefinition
     *       annotated:
     *         elements:
     *           - name: transactional
     *             value: false
     * </pre>
     * we need to check if the annotation elements (the different config properties defined within the annotation)
     * match the one(s) in the query. The query can define more than one element, as shown by the fact that the
     * "elements" node is an array, therefore, if multiple elements are present in the query, all must be matched.
     *
     * @param annotation the annotation to inspect
     * @return a boolean indicating whether the elements match
     * @throws JavaModelException
     */
    private boolean doElementsMatch(Annotation annotation) throws JavaModelException {
        // If the query has annotation elements to check, iterate through the annotation's values and check
        if (getAnnotationQuery().getElements() != null && !getAnnotationQuery().getElements().entrySet().isEmpty()) {
            IMemberValuePair[] memberValuePairs = annotation.getMemberValuePairs();
            Set<Map.Entry<String, String>> ruleAnnotationElems = getAnnotationQuery().getElements().entrySet();
            boolean allElementsMatch = true;
            boolean oneElementMatched = false;
            // TODO: there is a problem with defaults: they don't appear in the memberValuePairs so they cannot be matched
            for (int i = 0; i < memberValuePairs.length && allElementsMatch; i++) {
                IMemberValuePair member = memberValuePairs[i];
                for (Map.Entry<String, String> ruleAnnotationElem : ruleAnnotationElems) {
                    String ruleAnnotationElementName = ruleAnnotationElem.getKey();
                    if (ruleAnnotationElementName.equals(member.getMemberName())) {
                        // Member values can be arrays. In this case, lets iterate over it and compare:
                        if (member.getValue() instanceof Object[]) {
                            Object[] values = (Object[]) member.getValue();
                            // TODO: at the moment we are just toString()ing the values.
                            //  We might want to make this more sophisticated, relying on
                            //  member.getValueKind() to match on specific kinds. This however can match
                            boolean valueMatches = Arrays.stream(values).anyMatch(v -> Pattern.matches(ruleAnnotationElem.getValue(), v.toString()));
                            oneElementMatched |= valueMatches;
                            allElementsMatch &= valueMatches;
                        } else {
                            boolean valueMatches = Pattern.matches(ruleAnnotationElem.getValue(), member.getValue().toString());
                            oneElementMatched |= valueMatches;
                            allElementsMatch &= valueMatches;
                        }
                    }
                }
            }
            return oneElementMatched && allElementsMatch;
        }

        // No annotation elements, but the annotation itself matches
        return true;

    }

    private IAnnotation[] tryToGetAnnotations(SourceRefElement t) {
        try {
            return t.getAnnotations();
        } catch (JavaModelException e) {
            return new IAnnotation[]{};
        }
    }

    /**
     * Tries to extract the fqn of the annotation from the list of imports of the compilation unit.
     */
    private String getFQN(IAnnotation annotation) {
        String name = annotation.getElementName();
        if (Pattern.matches(".*\\.", name)) {
            // If the name of the annotation has a dot on it, it's a fqn
            return name;
        } else {
            // If not, the annotation must have been imported. Look in the imports:
            return tryToGetImports(annotation).stream()
                    .filter(i -> i.getElementName().endsWith(name))
                    .findFirst()
                    .map(IImportDeclaration::getElementName)
                    .orElse(name);
        }
    }

    private List<IImportDeclaration> tryToGetImports(IAnnotation annotation) {
        try {
            return Optional.ofNullable(((Annotation) annotation).getCompilationUnit().getImports()).map(Arrays::asList).orElse(List.of());
        } catch (Throwable e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
