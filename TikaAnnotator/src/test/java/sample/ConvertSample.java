package sample;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;

import java.io.FilterOutputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Kamil Podlesak &lt;kamil.podlesak@lmc.eu&gt;
 */
public class ConvertSample {

    public static final void main(String[] args) throws Exception {

        Parser parser = new AutoDetectParser();

        OutputStream os = new FilterOutputStream(System.out);
        Writer wr = new FilterWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            @Override
            public void write(int c) throws IOException {
                super.write(c);
                super.write('\u03b1');
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                super.write(cbuf, off, len);
                super.write('\u03b2');
            }

            @Override
            public void write(String str, int off, int len) throws IOException {
                super.write(str, off, len);
                super.write('\u03b3');
            }
        };
        ContentHandler out = new ToTextContentHandler(wr);

        for (String arg : args) {
            final Path path = Paths.get(arg);
            try (InputStream is = Files.newInputStream(path)) {
                parser.parse(is, out, new Metadata(), new ParseContext());
            }

        }
    }

}
