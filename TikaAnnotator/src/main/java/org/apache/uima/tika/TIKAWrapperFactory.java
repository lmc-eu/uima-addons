package org.apache.uima.tika;

import org.apache.tika.exception.TikaException;
import org.apache.uima.UimaContext;
import org.apache.uima.resource.ResourceInitializationException;

/**
 * Create and configure tika wrapper according to UIMA configuration; common part of both
 * {@link FileSystemCollectionReader} and {@link MarkupAnnotator}.
 */
public class TIKAWrapperFactory {

    public final static String tika_file_param = "tikaConfigFile";

    public static TIKAWrapper createTika(UimaContext ctx) throws ResourceInitializationException {
        String tikaConfigURL = (String) ctx.getConfigParameterValue(tika_file_param);
        try {
            if (tikaConfigURL == null) {
                return new TIKAWrapper();
            } else {
                return new TIKAWrapper(tikaConfigURL);
            }
        } catch (TikaException e) {
            throw new ResourceInitializationException(e);
        }
    }

}
