package org.apache.uima.tika;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.junit.Assert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Description of test data files.
 */
public class TestData {

    public static final TestData DEFAULT = new TestData(ImmutableMap.of(
            "apache.html", new TestingDocDescriptor(false, ImmutableMap.of(
                    "h3", new String[]{
                            "We consider ourselves not simply a group of projects sharing a server, but rather a community of developers and users.",
                            "The Apache Software Foundation provides support for the Apache community of open-source software projects.",
                            "The Apache projects are defined by collaborative consensus based processes, an open, pragmatic software license and a desire to create high quality software that leads the way in its field.",
                            "Latest News",
                            "Latest Activity",
                    }
            )),
            "references.pdf", new TestingDocDescriptor(false, ImmutableMap.of(
                    "h3", ArrayUtils.EMPTY_STRING_ARRAY,
                    "em", ArrayUtils.EMPTY_STRING_ARRAY
            )),
            "tika-parsing-test.docx", new TestingDocDescriptor(true, ImmutableMap.of(
                    "b", new String[]{"License and Disclaimer.", "Trademarks."}
            )),
            "tika-parsing-test.odt", new TestingDocDescriptor(true, ImmutableMap.of(
                    "b", new String[]{"License and Disclaimer.", "Trademarks."}
            ))
    ));


    static class TestingDocDescriptor {
        final boolean printMarkup;
        /**
         * Map: key = element name (lowercase), value = array of expected values.
         */
        final Map<String, String[]> expectedValues;

        public TestingDocDescriptor(boolean printMarkup, Map<String, String[]> expectedValues) {
            this.printMarkup = printMarkup;
            this.expectedValues = expectedValues;
        }
    }


    protected final Map<String, TestingDocDescriptor> testingDocs;

    public TestData(Map<String, TestingDocDescriptor> testingDocs) {
        this.testingDocs = testingDocs;
    }

    public Set<String> getFilenames() {
        return testingDocs.keySet();
    }

    /**
     * Check results.
     * @param results             collection of result CAS documents
     * @param filenameProvider    optional: function that provides source filename for each CAS;
     *                            <br />when not available, the CAS document must contain URL fature in SourceDocumentAnnotation!
     */
    public void checkResults(Iterable<JCas> results, Optional<Function<JCas, String>> filenameProvider) {
        int count = 0;
        final Set<String> parsedFilenames = new TreeSet<>();
        Map<String, Map<String, List<String>>> elementTexts = new TreeMap<>();
        for (JCas result : results) {
            count++;
            System.out.println();
            System.out.println("----------------------------------------------------------------------------------");
            System.out.println();

            final SourceDocumentAnnotation aSource = JCasUtil.selectSingle(result, SourceDocumentAnnotation.class);

            String url = fetchDocumentFeature(aSource, "url");
            System.out.println("  url: " + url);
            System.out.println("  content-type: " + fetchDocumentFeature(aSource, "Content-Type"));

            final String filename;
            if (filenameProvider.isPresent()) {
                filename = filenameProvider.get().apply(result);
                Assert.assertNotNull(filename);
            } else {
                Assert.assertNotNull(url);
                filename = url.replaceFirst("^.*/", "");
            }
            System.out.println("  filename: " + filename);
            parsedFilenames.add(filename);
            final TestData.TestingDocDescriptor descr = testingDocs.get(filename);
            Assert.assertNotNull("WTF? unknown parsed file " + filename, descr);

            //find bold texts
            elementTexts.put(filename, descr.expectedValues.keySet().stream().collect(Collectors.toMap(
                    Function.identity(), elemName -> findElementMarkup(result, elemName)
            )));

            if (descr.printMarkup) {
                printMarkups(result, aSource);
            }

        }
        Assert.assertEquals(testingDocs.size(), count);
        Assert.assertEquals(testingDocs.keySet(), parsedFilenames);

        for (Map.Entry<String, Map<String, List<String>>> ee : elementTexts.entrySet()) {
            final TestData.TestingDocDescriptor descr = testingDocs.get(ee.getKey());
            for (Map.Entry<String, List<String>> le : ee.getValue().entrySet()) {
                final List<String> foundValues = le.getValue();
                Assert.assertArrayEquals(
                        "invalid values for document " + ee.getKey() + ", element " + le.getKey(),
                        descr.expectedValues.get(le.getKey()),
                        foundValues.toArray(new String[foundValues.size()])
                );
            }
        }
    }

    private static final Pattern CUTOFF_LINES = Pattern.compile("[\n\r].*", Pattern.DOTALL);

    private void printMarkups(JCas result, SourceDocumentAnnotation aSource) {
//        System.out.println(Arrays.toString(aSource.getFeatures().toStringArray()));
        System.out.println("  markups:");
        for (MarkupAnnotation a : JCasUtil.select(result, MarkupAnnotation.class)) {
            //ignore empty annotations...
            if (a.getBegin() == a.getEnd()) {
                continue;
            }
            String text = a.getCoveredText();
            if (text.length() > 60) {
                text = text.substring(0, 55) + "\u2026";
            }
            text = CUTOFF_LINES.matcher(text).replaceAll("\u2026\\\\n");
            System.out.printf("     %s(%d-%d): %s%n", a.getName(), a.getBegin(), a.getEnd(), text);
        }
    }

    private List<String> findElementMarkup(JCas result, String elementName) {
        return JCasUtil.select(result, MarkupAnnotation.class).stream()
                .filter(a -> elementName.equalsIgnoreCase(a.getName()))
                .map(MarkupAnnotation::getCoveredText)
                .map(String::trim)
                .collect(Collectors.toList());
    }

    protected String fetchDocumentFeature(SourceDocumentAnnotation annotation, String featureName) {
        final FSArray features = annotation.getFeatures();
        final int size = features.size();
        for (int i = 0; i < size; i++) {
            FeatureValue value = (FeatureValue) features.get(i);
            if (value.getName().equals(featureName)) {
                return value.getValue();
            }
        }
        return null;
    }


}
