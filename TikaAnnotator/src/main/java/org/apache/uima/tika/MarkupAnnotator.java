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

import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import java.io.IOException;
import java.io.InputStream;


/**
 * Uses TIKA to convert original markup into UIMA annotations*
 */
@SuppressWarnings("StringBufferReplaceableByString")
public class MarkupAnnotator extends CasAnnotator_ImplBase {


    private final static String ORIGINAL_VIEW_PARAM_NAME = "ORIGINAL_VIEW_PARAM_NAME";
    private final static String TEXT_VIEW_PARAM_NAME = "TEXT_VIEW_PARAM_NAME";

    // takes an option indicating the name of the view containing the binary document
    private String originalViewName = CAS.NAME_DEFAULT_SOFA;

    // takes an option indicating the name of the view containing the text version of the document
    private String textViewName = "textView";

    // Tika wrapper
    private TIKAWrapper tika;


    private String getConfigValue(UimaContext aContext, String paramName, String defaultValue) {
        final String value = (String) aContext.getConfigParameterValue(paramName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        // Get config param setting
        originalViewName = getConfigValue(aContext, ORIGINAL_VIEW_PARAM_NAME, CAS.NAME_DEFAULT_SOFA);

        textViewName = getConfigValue(aContext, TEXT_VIEW_PARAM_NAME, "textView");

        // initialise TIKA parser
        // try to get a custom config
        // initialise TIKA parser
        tika = TIKAWrapperFactory.createTika(aContext);
    }

    public void process(CAS cas) throws AnalysisEngineProcessException {
        CAS originalCas;
        try {
            originalCas = cas.getView(originalViewName);
        } catch (Exception e) {
            String viewName = cas.getViewName();
            // can't find originalViewName
            this.getContext().getLogger().log(Level.WARNING, new StringBuffer("can't find view ").append(originalViewName)
                    .append(" using ").append(viewName).append(" instead").toString());
            originalCas = cas.getCurrentView();
        }

        // TODO if content type is known then we use it
        // otherwise we guess

        CAS plainTextView = cas.createView(textViewName);
        try (InputStream is = originalCas.getSofaDataStream()) {
            tika.populateCASfromURL(plainTextView, is, null, null, null);
        } catch (IOException | CASException e) {
            throw new AnalysisEngineProcessException(e);
        }

        JCas ptv;
        try {
            ptv = plainTextView.getJCas();
        } catch (CASException e) {
            //this should never happen...
            throw new AnalysisEngineProcessException(e);
        }
    /* identify language */
        extractLanguage(ptv);
    }

    private void extractLanguage(JCas plainTextView) {
        try {
            LanguageIdentifier li = new LanguageIdentifier(new LanguageProfile(plainTextView.getDocumentText()));
            if (li.getLanguage() != null && !"".equals(li.getLanguage()))
                plainTextView.setDocumentLanguage(li.getLanguage());
        } catch (Exception e) {
            this.getContext().getLogger().log(Level.WARNING, new StringBuffer("Could not extract language due to ")
                    .append(e.getLocalizedMessage()).toString());
        }
        this.getContext().getLogger().log(Level.INFO, new StringBuffer("Extracted language: ").append(plainTextView
                .getDocumentLanguage()).toString());
    }

}
