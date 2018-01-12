/**
 * Copyright © 2017, viadee Unternehmensberatung GmbH All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met: 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer. 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or other materials provided with the
 * distribution. 3. All advertising materials mentioning features or use of this software must display the following
 * acknowledgement: This product includes software developed by the viadee Unternehmensberatung GmbH. 4. Neither the
 * name of the viadee Unternehmensberatung GmbH nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <viadee Unternehmensberatung GmbH> ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing.checker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.model.bpmn.instance.BaseElement;

import de.viadee.bpm.vPAV.AbstractRunner;
import de.viadee.bpm.vPAV.BPMNScanner;
import de.viadee.bpm.vPAV.config.model.Rule;
import de.viadee.bpm.vPAV.config.model.Setting;
import de.viadee.bpm.vPAV.processing.CheckName;
import de.viadee.bpm.vPAV.processing.model.data.BpmnElement;
import de.viadee.bpm.vPAV.processing.model.data.CheckerIssue;
import de.viadee.bpm.vPAV.processing.model.data.CriticalityEnum;

public class ExtensionChecker extends AbstractElementChecker {

    public ExtensionChecker(Rule rule, BPMNScanner bpmnScanner) {
        super(rule, bpmnScanner);
    }

    @Override
    public Collection<CheckerIssue> check(BpmnElement element) {
        final Collection<CheckerIssue> issues = new ArrayList<CheckerIssue>();
        final BaseElement bpmnElement = element.getBaseElement();
        final Map<String, Setting> settings = rule.getSettings();
        final ArrayList<String> whiteList = rule.getWhiteList();

        final Map<String, String> keyPairs = new HashMap<String, String>();
        final ArrayList<Setting> optionalSettings = new ArrayList<Setting>();
        final ArrayList<Setting> mandatorySettings = new ArrayList<Setting>();

        // Retrieve extension key pair from bpmn model
        keyPairs.putAll(bpmnScanner.getKeyPairs(bpmnElement.getId()));

        // Create ArrayList for easier manipulation
        for (Map.Entry<String, Setting> settingsEntry : settings.entrySet()) {
            if (settingsEntry.getValue().getRequired()) {
                mandatorySettings.add(settingsEntry.getValue());
            } else {
                optionalSettings.add(settingsEntry.getValue());
            }
        }

        if (whiteList.contains(bpmnElement.getElementType().getInstanceType().getSimpleName())) {
            // Check for all mandatory extension pairs according to ruleset
            issues.addAll(checkManExtension(whiteList, mandatorySettings, keyPairs, bpmnElement, element));
            // Check for all optional extension pairs
            issues.addAll(checkOptExtension(whiteList, optionalSettings, keyPairs, bpmnElement, element));
        }

        return issues;

    }

    /**
     * Checks all elements for mandatory settings
     *
     * @param settings
     *            Mandatory settings as specified by ruleset
     * @param keyPairs
     *            Extension key pairs
     * @param bpmnElement
     *            BpmnElement
     * @param element
     *            Element
     * @return
     */
    private Collection<CheckerIssue> checkManExtension(final ArrayList<String> whiteList,
            final ArrayList<Setting> settings,
            final Map<String, String> keyPairs, final BaseElement bpmnElement, final BpmnElement element) {
        final Collection<CheckerIssue> issues = new ArrayList<CheckerIssue>();

        for (Setting setting : settings) {
            checkKey(whiteList, keyPairs, bpmnElement, element, issues, setting);
        }

        return issues;
    }

    /**
     *
     * Checks a certain setting against validity of key-value extension pair
     *
     * @param whiteList
     *            whiteList contains types of task to check
     * @param keyPairs
     *            Extension key pairs
     * @param bpmnElement
     *            BpmnElement
     * @param element
     *            Element
     * @param issues
     *            List of issues
     * @param setting
     *            Concrete setting
     */
    private void checkKey(final ArrayList<String> whiteList, final Map<String, String> keyPairs,
            final BaseElement bpmnElement, final BpmnElement element, final Collection<CheckerIssue> issues,
            Setting setting) {
        // Check whether rule for ExtensionChecker is misconfigured
        if (!checkMisconfiguration(setting)) {

            // Type is specified in ruleSet
            if (setting.getType() != null && setting.getId() == null) {

                // Check whether specified type equals name of bpmnElement
                if (setting.getType().equals(bpmnElement.getElementType().getInstanceType().getSimpleName())) {

                    // Check whether the key specified in the settings is contained in the model
                    if (setting.getName() != null && keyPairs.containsKey(setting.getName())) {
                        checkValue(keyPairs, bpmnElement, element, issues, setting, false);
                    } else {
                        issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                                element.getProcessdefinition(), null,
                                bpmnElement.getAttributeValue("id"),
                                bpmnElement.getAttributeValue("name"), null, null, null,
                                "Key of '" + CheckName.checkName(bpmnElement)
                                        + "' could not be resolved. The ruleset specifies the use of key '"
                                        + setting.getName() + "'."));
                    }
                }
            }

            // Check based on ID
            if (setting.getType() == null && setting.getId() != null) {
                if (setting.getId().equals(bpmnElement.getAttributeValue("id"))) {
                    checkValue(keyPairs, bpmnElement, element, issues, setting, false);
                    AbstractRunner.setIdFound(true);
                }
            }

            // Check all elements
            if (setting.getType() == null && setting.getId() == null) {

                if (setting.getName() != null && keyPairs.containsKey(setting.getName())) {
                    checkValue(keyPairs, bpmnElement, element, issues, setting, false);
                } else {
                    issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                            element.getProcessdefinition(), null,
                            bpmnElement.getAttributeValue("id"),
                            bpmnElement.getAttributeValue("name"), null, null, null,
                            "Key of '" + CheckName.checkName(bpmnElement)
                                    + "' could not be resolved. The ruleset specifies the use of key '"
                                    + setting.getName() + "'."));
                }

            }
        }
    }

    /**
     * Checks all elements with optional settings
     *
     * @param settings
     *            Mandatory settings as specified by ruleset
     * @param keyPairs
     *            Extension key pairs
     * @param bpmnElement
     *            BpmnElement
     * @param element
     *            Element
     * @return
     */
    private Collection<CheckerIssue> checkOptExtension(final ArrayList<String> whiteList,
            final ArrayList<Setting> settings,
            final Map<String, String> keyPairs, final BaseElement bpmnElement, final BpmnElement element) {
        final Collection<CheckerIssue> issues = new ArrayList<CheckerIssue>();

        for (Setting setting : settings) {

            if (!checkMisconfiguration(setting)) {

                // Type is specified in ruleSet
                if (setting.getType() != null) {

                    // Check whether specified type equals name of bpmnElement
                    if (setting.getType().equals(bpmnElement.getElementType().getInstanceType().getSimpleName())) {

                        // Check whether the key specified in the settings is contained in the model
                        if (setting.getName() != null && keyPairs.containsKey(setting.getName())) {
                            checkValue(keyPairs, bpmnElement, element, issues, setting, true);
                        }
                    }
                } else {
                    // Check based on ID
                    if (setting.getId() != null
                            && setting.getId().equals(bpmnElement.getAttributeValue("id"))) {
                        checkValue(keyPairs, bpmnElement, element, issues, setting, true);
                    } else {
                        if (whiteList.contains(bpmnElement.getElementType().getInstanceType().getSimpleName())) {
                            if (setting.getName() != null && keyPairs.containsKey(setting.getName())) {
                                checkValue(keyPairs, bpmnElement, element, issues, setting, true);
                            }
                        }
                    }
                }
            }
        }
        return issues;
    }

    /**
     * Checks the value of a given key-value pair
     *
     * @param setting
     *            Certain setting out of all settings
     * @param keyPairs
     *            Extension key pairs
     * @param bpmnElement
     *            BpmnElement
     * @param element
     *            Element
     * @param issues
     *            List of Issues
     */
    private void checkValue(final Map<String, String> keyPairs, final BaseElement bpmnElement,
            final BpmnElement element, final Collection<CheckerIssue> issues, final Setting setting,
            final Boolean check) {
        if (keyPairs.get(setting.getName()) != null && !keyPairs.get(setting.getName()).isEmpty()) {

            final String patternString = setting.getValue();
            final Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(keyPairs.get(setting.getName()));

            // if predefined value of a key-value pair does not fit a given regex (e.g. digits for
            // timeout)
            if (!matcher.matches()) {
                issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                        element.getProcessdefinition(), null,
                        bpmnElement.getAttributeValue("id"),
                        bpmnElement.getAttributeValue("name"), null, null, null,
                        "Key-Value pair of '" + CheckName.checkName(bpmnElement)
                                + "' does not fit the configured setting of the rule set. Check the extension with key '"
                                + setting.getName() + "'."));
            }
        } else {
            if (!check) {
                issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                        element.getProcessdefinition(), null,
                        bpmnElement.getAttributeValue("id"),
                        bpmnElement.getAttributeValue("name"), null, null, null,
                        "Value of '" + CheckName.checkName(bpmnElement)
                                + "' is empty. Check whether ruleset and model are congruent."));
            }
        }
    }

    /**
     * Checks whether a misconfiguration of the ruleSet.xml occured
     *
     * @param setting
     *            Certain setting out of all settings
     */
    private boolean checkMisconfiguration(Setting setting) {

        boolean misconfigured = false;

        if (setting.getType() != null && setting.getId() != null) {
            misconfigured = true;
            AbstractRunner.setIsMisconfigured(misconfigured);
            return misconfigured;
        }
        return misconfigured;
    }

}
