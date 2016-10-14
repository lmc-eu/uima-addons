package sample;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToHTMLContentHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Kamil Podlesak &lt;kamil.podlesak@lmc.eu&gt;
 */
public class ConvertSample {

    public static final void main(String[] args) throws Exception {

        Parser parser = new AutoDetectParser();

        OutputStream os = System.out;

        for (String arg : args) {
            final Path path = Paths.get(arg);
            try (InputStream is = Files.newInputStream(path)) {
                final ToHTMLContentHandler out = new ToHTMLContentHandler(os, "utf-8");
                parser.parse(is, out, new Metadata(), new ParseContext());
            }

        }
    }

}
