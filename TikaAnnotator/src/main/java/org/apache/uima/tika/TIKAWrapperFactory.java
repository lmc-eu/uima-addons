package org.apache.uima.tika;

import org.apache.tika.exception.TikaException;
import org.apache.uima.UimaContext;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Logger;

import java.util.EnumSet;
import java.util.Set;

/**
 * Create and configure tika wrapper according to UIMA configuration; common part of both
 * {@link FileSystemCollectionReader} and {@link MarkupAnnotator}.
 */
public class TIKAWrapperFactory {

    public final static String PARAM_TIKA_FILE = "tikaConfigFile";
    public final static String PARAM_DETECT_LANGUAGE = "tikaLanguageDetector";

    public static TIKAWrapper createTika(UimaContext ctx) throws ResourceInitializationException {
        String tikaConfigURL = (String) ctx.getConfigParameterValue(PARAM_TIKA_FILE);

        Object enableLangDetector = ctx.getConfigParameterValue(PARAM_DETECT_LANGUAGE);
        Set<TIKAWrapper.OptionalTikaFeature> features = EnumSet.noneOf(TIKAWrapper.OptionalTikaFeature.class);
        if (enableLangDetector != null &&
                (Boolean.TRUE.equals(enableLangDetector) || "true".equalsIgnoreCase(enableLangDetector.toString()))) {
            features.add(TIKAWrapper.OptionalTikaFeature.LANGUAGE_DETECTOR);
        }

        final Logger logger = ctx.getLogger();
        try {
            return new TIKAWrapper(logger, tikaConfigURL, features);
        } catch (TikaException e) {
            throw new ResourceInitializationException(e);
        }
    }

}
