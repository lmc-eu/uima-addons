package org.apache.uima.tika;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test parsing of PDF file.
 *
 * @author Kamil Podlesak &lt;kamil.podlesak@lmc.eu&gt;
 */
public class FileSystemCollectionReaderTest {

    protected static final List<String> TESTING_DOCS = Arrays.asList("apache.html", "references.pdf");

    private static final Pattern CUTOFF_LINES = Pattern.compile("[\n\r].*", Pattern.DOTALL);

    protected Path inputDir;
    protected Path outputDir;

    @Before
    public void prepareDirectories() throws IOException {
        inputDir = Files.createTempDirectory("TikaAnnotatorTestInput");
        outputDir = Files.createTempDirectory("TikaAnnotatorTestOuput");

        //and copy input content
        for (String filename : TESTING_DOCS) {
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
        Map<String, List<String>> h3Texts = new TreeMap<>();
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

            //find bold texts
            h3Texts.put(filename, JCasUtil.select(result, MarkupAnnotation.class).stream()
                    .filter(a -> "h3".equalsIgnoreCase(a.getName()))
                    .map(MarkupAnnotation::getCoveredText)
                    .collect(Collectors.toList())
            );

/*
            System.out.println(Arrays.toString(aSource.getFeatures().toStringArray()));
            System.out.println("markups:");
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
                System.out.printf("   %s(%d-%d): %s%n", a.getName(), a.getBegin(), a.getEnd(), text);
            }

 */
        }
        Assert.assertEquals(TESTING_DOCS.size(), count);
        Assert.assertEquals(new TreeSet<>(TESTING_DOCS), parsedFilenames);

        Assert.assertEquals(Collections.emptyList(), h3Texts.get("references.pdf"));
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        Assert.assertArrayEquals(new String[]{
                "We consider ourselves not simply a group of projects sharing a server, but rather a community of developers and users.",
                "The Apache Software Foundation provides support for the Apache community of open-source software projects.",
                "The Apache projects are defined by collaborative consensus based processes, an open, pragmatic software license and a desire to create high quality software that leads the way in its field.",
                "Latest News",
                "Latest Activity",
        }, h3Texts.get("apache.html").toArray(new String[0]));

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