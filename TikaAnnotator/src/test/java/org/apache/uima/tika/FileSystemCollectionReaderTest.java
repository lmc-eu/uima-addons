package org.apache.uima.tika;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test parsing of PDF file.
 *
 * @author Kamil Podlesak &lt;kamil.podlesak@lmc.eu&gt;
 */
public class FileSystemCollectionReaderTest {

    protected static final Map<String, TestingDocDescriptor> TESTING_DOCS = ImmutableMap.of(
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
    );

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


    //-------------------------------------------------------------------------------------------------------------


    protected Path inputDir;
    protected Path outputDir;

    @Before
    public void prepareDirectories() throws IOException {
        inputDir = Files.createTempDirectory("TikaAnnotatorTestInput");
        outputDir = Files.createTempDirectory("TikaAnnotatorTestOuput");

        //and copy input content
        for (String filename : TESTING_DOCS.keySet()) {
            final URL url = getClass().getResource("/docs/" + filename);
            Assert.assertNotNull("testing resource " + filename + " not found", url);
            final Path outputFile = inputDir.resolve(filename);
            System.out.printf("copying: %s -> %s%n", url, outputFile);
            try (final InputStream is = url.openStream()) {
                Files.copy(is, outputFile);
            }
        }

    }

    private void deleteDir(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            //and continue...
        }
    }

    @After
    public void cleanupDirectories() throws IOException {
        Stream.of(inputDir, outputDir).filter(Objects::nonNull).filter(Files::isDirectory).forEach(this::deleteDir);
    }

    @Test
    public void testCollectionReader() throws UIMAException, IOException {
        TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescriptionFromPath(
                getClass().getResource("/MarkupAnnotationTypeSystem.xml").toString()
        );
        CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                FileSystemCollectionReader.class,
                typeSystem,
                FileSystemCollectionReader.PARAM_INPUTDIR, inputDir.toAbsolutePath().toString()
        );
        reader.doFullValidation();
        System.out.println("starting processing of files: " + TESTING_DOCS);
        int count = 0;
        final Set<String> parsedFilenames = new TreeSet<>();
        Map<String, Map<String, List<String>>> elementTexts = new TreeMap<>();
        for (JCas result : SimplePipeline.iteratePipeline(reader)) {
            count++;
            System.out.println();
            System.out.println("----------------------------------------------------------------------------------");
            System.out.println();

            final SourceDocumentAnnotation aSource = JCasUtil.selectSingle(result, SourceDocumentAnnotation.class);

            String url = fetchDocumentFeature(aSource, "url");
            System.out.println("  url: " + url);
            System.out.println("  content-type: " + fetchDocumentFeature(aSource, "Content-Type"));

            Assert.assertNotNull(url);
            String filename = url.replaceFirst("^.*/", "");
            parsedFilenames.add(filename);
            final TestingDocDescriptor descr = TESTING_DOCS.get(filename);
            Assert.assertNotNull("WTF? unknown parsed file " + filename, descr);

            //find bold texts
            elementTexts.put(filename, descr.expectedValues.keySet().stream().collect(Collectors.toMap(
                    Function.identity(), elemName -> findElementMarkup(result, elemName)
            )));

            if (descr.printMarkup) {
                printMarkups(result, aSource);
            }

        }
        Assert.assertEquals(TESTING_DOCS.size(), count);
        Assert.assertEquals(TESTING_DOCS.keySet(), parsedFilenames);

        for (Map.Entry<String, Map<String, List<String>>> ee : elementTexts.entrySet()) {
            final TestingDocDescriptor descr = TESTING_DOCS.get(ee.getKey());
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