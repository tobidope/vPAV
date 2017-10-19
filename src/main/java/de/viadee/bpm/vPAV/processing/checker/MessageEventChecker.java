/**
 * Copyright � 2017, viadee Unternehmensberatung GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    This product includes software developed by the viadee Unternehmensberatung GmbH.
 * 4. Neither the name of the viadee Unternehmensberatung GmbH nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <viadee Unternehmensberatung GmbH> ''AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing.checker;

import java.util.ArrayList;
import java.util.Collection;

import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;

import de.viadee.bpm.vPAV.config.model.Rule;
import de.viadee.bpm.vPAV.processing.CheckName;
import de.viadee.bpm.vPAV.processing.model.data.BpmnElement;
import de.viadee.bpm.vPAV.processing.model.data.CheckerIssue;
import de.viadee.bpm.vPAV.processing.model.data.CriticalityEnum;

public class MessageEventChecker extends AbstractElementChecker {

    public MessageEventChecker(final Rule rule, final String path) {
        super(rule, path);
    }

    /**
     * Check MessageEvents for implementation and messages
     *
     * @return issues
     */
    @Override
    public Collection<CheckerIssue> check(BpmnElement element) {

        final Collection<CheckerIssue> issues = new ArrayList<CheckerIssue>();
        final BaseElement baseElement = element.getBaseElement();

        if (baseElement.getElementType().getTypeName()
                .equals(BpmnModelConstants.BPMN_ELEMENT_END_EVENT)
                || baseElement.getElementType().getTypeName()
                        .equals(BpmnModelConstants.BPMN_ELEMENT_START_EVENT)
                || baseElement.getElementType().getTypeName()
                        .equals(BpmnModelConstants.BPMN_ELEMENT_INTERMEDIATE_CATCH_EVENT)
                || baseElement.getElementType().getTypeName()
                        .equals(BpmnModelConstants.BPMN_ELEMENT_INTERMEDIATE_THROW_EVENT)
                || baseElement.getElementType().getTypeName()
                        .equals(BpmnModelConstants.BPMN_ELEMENT_BOUNDARY_EVENT)) {

            final Event event = (Event) baseElement;
            final Collection<MessageEventDefinition> messageEventDefinitions = event
                    .getChildElementsByType(MessageEventDefinition.class);
            if (messageEventDefinitions != null) {
                for (MessageEventDefinition eventDef : messageEventDefinitions) {
                    if (eventDef != null) {
                        final Message message = eventDef.getMessage();
                        if (message == null || message.getName() == null || message.getName().isEmpty()) {
                            issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                                    element.getProcessdefinition(), null, baseElement.getAttributeValue("id"),
                                    baseElement.getAttributeValue("name"), null, null, null,
                                    "No message has been specified for '" + CheckName.checkName(baseElement)
                                            + "'."));
                        }
                    }
                }
            }
        }
        return issues;
    }

}
