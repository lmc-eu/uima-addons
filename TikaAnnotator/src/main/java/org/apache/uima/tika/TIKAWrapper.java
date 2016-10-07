/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.tika;

import com.google.common.collect.Sets;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;


public class TIKAWrapper {

    public enum OptionalTikaFeature {
        /**
         * Use Tika's internal langauge detector; it's not only deprecated, but also very very limited (only
         * a handful of languages). For these reasons, it's reccomended to NOT use it.
         */
        LANGUAGE_DETECTOR
    }

    protected final Logger logger;
    protected final Set<OptionalTikaFeature> enabledFeatures;
    // configuration for TIKA - can be created by specifying a custom resource
    private TikaConfig config;

    public TIKAWrapper(Logger logger, String configLocation, OptionalTikaFeature... features) throws TikaException {
        this(logger, configLocation, Sets.newEnumSet(Arrays.asList(features), OptionalTikaFeature.class));
    }

    public TIKAWrapper(Logger logger, String configLocation, Set<OptionalTikaFeature> features) throws TikaException {
        this.logger = logger;
        this.enabledFeatures = features;
        if (configLocation != null)
            try {
                config = new TikaConfig(configLocation);
            } catch (Exception ignored) {
            }
        if (config == null)
            config = TikaConfig.getDefaultConfig();
    }


    public void populateCASfromURL(CAS cas, URL url, String mime, String language) throws CASException, IOException {
        try (InputStream is = url.openStream(); InputStream originalStream = new BufferedInputStream(is)) {
            populateCASfromURL(cas, originalStream, url, mime, language);
        }
    }

    public void populateCASfromURL(CAS cas, InputStream originalStream,
                                   URL url, String mime, String language) throws IOException, CASException {

        // use custom parser or rely on autodetect
        Parser parser = config.getParser();

        // it that does not work
        if (parser == null || parser instanceof DefaultParser) {
            parser = new AutoDetectParser(config);
        }

        Metadata md = new Metadata();
        MarkupHandler handler = new MarkupHandler();

        try {
            parser.parse(originalStream, handler, md, new ParseContext());
        } catch (Exception e) {
            // if we have a problem just dump the message and continue
            // getLogger().log(Level.WARNING,"Problem converting file : "+URI+"\t"+e.getMessage());
            // cas.setDocumentText(""); return;
            throw new CASException(e);
        } finally {
            // set language if it was explicitly specified as a configuration
            // parameter
            if (language != null) {
                cas.setDocumentLanguage(language);
            }
            try {
                originalStream.close();
            } catch (IOException ignored) {
            }
        }

        // add text and markup to CAS
        handler.populateCAS(cas);

        JCas jcas = cas.getJCas();

        SourceDocumentAnnotation docAnnotation = new SourceDocumentAnnotation(jcas);

        //add URL as metadata, if present
        if (url != null) {
            md.add("url", url.toString());
        }

        // now iterate on the metadata found by Tika and add them to the info
        if (docAnnotation.getFeatures() == null) {
            docAnnotation.setFeatures((FSArray) cas
                    .createArrayFS(md.size())
            );
        }
        int i = 0;
        for (; i < md.size(); i++) {
            String name = md.names()[i];
            String value = md.get(name);
            FeatureValue fv = new FeatureValue(cas.getJCas());
            fv.setName(name);
            fv.setValue(value);
            // getLogger().log(Level.FINER,URI+"\t"+name+"\t"+value);
            docAnnotation.setFeatures(i, fv);
        }

        docAnnotation.addToIndexes();

        //extract language
        if (enabledFeatures.contains(OptionalTikaFeature.LANGUAGE_DETECTOR)) {
            extractLanguage(jcas);
        }
    }

    @SuppressWarnings("deprecation")
    private void extractLanguage(JCas plainTextView) {
        try {
            LanguageIdentifier li = new LanguageIdentifier(new LanguageProfile(plainTextView.getDocumentText()));
            if (li.getLanguage() != null && !"".equals(li.getLanguage()))
                plainTextView.setDocumentLanguage(li.getLanguage());
        } catch (Exception e) {
            logger.log(Level.INFO, "Could not extract language due to " + e);
        }
        logger.log(Level.FINE, "Extracted language: " + plainTextView.getDocumentLanguage());
    }

}