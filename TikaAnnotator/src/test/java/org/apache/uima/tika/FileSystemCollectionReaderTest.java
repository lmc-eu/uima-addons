package org.apache.uima.tika;

import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
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
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Test parsing of PDF file.
 *
 * @author Kamil Podlesak &lt;kamil.podlesak@lmc.eu&gt;
 */
public class FileSystemCollectionReaderTest {


    protected final TestData TESTING_DOCS = TestData.DEFAULT;

    //-------------------------------------------------------------------------------------------------------------


    protected Path inputDir;
    protected Path outputDir;

    @Before
    public void prepareDirectories() throws IOException {
        inputDir = Files.createTempDirectory("TikaAnnotatorTestInput");
        outputDir = Files.createTempDirectory("TikaAnnotatorTestOuput");

        //and copy input content
        for (String filename : TESTING_DOCS.getFilenames()) {
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
        System.out.println("starting processing of files: " + TESTING_DOCS.getFilenames());
        final Iterable<JCas> results = SimplePipeline.iteratePipeline(reader);
        TESTING_DOCS.checkResults(results);
    }

}