/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelException;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Documentation;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.Transaction;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AbstractBpmnModelElementBuilder}.
 */
class AbstractBpmnModelElementBuilderTest {
  @Test
  void testDoneReturnsModelInstance() {
      // given
      final BpmnModelInstance modelInstance =
          Bpmn.createExecutableProcess("process").startEvent().done();
  
      // then
      assertThat(modelInstance).isNotNull();
  }

  @Test
  void testGetElementReturnsElement() {
      // given
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent("myStart");
  
      // when
      final StartEvent element = builder.getElement();
  
      // then
      assertThat(element).isNotNull();
      assertThat(element.getId()).isEqualTo("myStart");
  }

  @Test
  void testSubProcessDoneReturnsSubProcessBuilder() {
      // given - a start event builder whose scope is a SubProcess
      final SubProcessBuilder subProcessBuilder =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .subProcess("mySubProcess")
              .embeddedSubProcess()
              .startEvent("innerStart")
              .subProcessDone();
  
      // then
      assertThat(subProcessBuilder).isNotNull();
      assertThat(subProcessBuilder.getElement()).isInstanceOf(SubProcess.class);
  }

  @Test
  void testSubProcessDoneThrowsExceptionWhenNoParentSubProcess() {
      // given - a start event builder at the process level (scope is Process, not SubProcess)
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent("start");
  
      // then
      assertThatThrownBy(builder::subProcessDone)
          .isInstanceOf(BpmnModelException.class)
          .hasMessageContaining("Unable to find a parent subProcess");
  }

  @Test
  void testTransactionDoneReturnsTransactionBuilder() {
      // given - a start event inside a transaction sub-process
      final TransactionBuilder transactionBuilder =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .transaction("myTransaction")
              .embeddedSubProcess()
              .startEvent("innerStart")
              .transactionDone();
  
      // then
      assertThat(transactionBuilder).isNotNull();
      assertThat(transactionBuilder.getElement()).isInstanceOf(Transaction.class);
  }

  @Test
  void testTransactionDoneThrowsExceptionWhenNoParentTransaction() {
      // given - a start event builder at the process level (scope is Process, not Transaction)
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent("start");
  
      // then
      assertThatThrownBy(builder::transactionDone)
          .isInstanceOf(BpmnModelException.class)
          .hasMessageContaining("Unable to find a parent transaction");
  }

  @Test
  void testThrowEventDefinitionDoneWithIntermediateThrowEventParent() {
      // given - a MessageEventDefinitionBuilder whose parent DOM element is an IntermediateThrowEvent
      final MessageEventDefinitionBuilder defBuilder =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .intermediateThrowEvent("ite")
              .messageEventDefinition("msgDef");
  
      // when
      final AbstractThrowEventBuilder<?, ?> result = defBuilder.throwEventDefinitionDone();
  
      // then
      assertThat(result).isInstanceOf(IntermediateThrowEventBuilder.class);
      assertThat(result.getElement()).isInstanceOf(IntermediateThrowEvent.class);
      assertThat(((IntermediateThrowEvent) result.getElement()).getId()).isEqualTo("ite");
  }

  @Test
  void testThrowEventDefinitionDoneWithEndEventParent() {
      // given - a MessageEventDefinitionBuilder whose parent DOM element is an EndEvent
      final MessageEventDefinitionBuilder defBuilder =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .endEvent("myEnd")
              .messageEventDefinition("msgDef");
  
      // when
      final AbstractThrowEventBuilder<?, ?> result = defBuilder.throwEventDefinitionDone();
  
      // then
      assertThat(result).isInstanceOf(EndEventBuilder.class);
      assertThat(result.getElement()).isInstanceOf(EndEvent.class);
      assertThat(((EndEvent) result.getElement()).getId()).isEqualTo("myEnd");
  }

  @Test
  void testThrowEventDefinitionDoneThrowsExceptionWhenNoValidParentEvent() {
      // given - a MessageEventDefinitionBuilder whose parent DOM element is a StartEvent
      // (not IntermediateThrowEvent or EndEvent), produced via a catch event builder
      final MessageEventDefinitionBuilder defBuilder =
          Bpmn.createExecutableProcess("process")
              .startEvent("start")
              .message("myMsg")
              // get the start event's messageEventDefinition builder - parent is StartEvent
              .messageEventDefinition();
  
      // then
      assertThatThrownBy(defBuilder::throwEventDefinitionDone)
          .isInstanceOf(BpmnModelException.class)
          .hasMessageContaining("Unable to find a parent event");
  }

  @Test
  void testDocumentationAddsChildElement() {
      // given
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent("start");
  
      // when
      final AbstractBpmnModelElementBuilder<?, ?> result =
          builder.documentation("This is a description");
  
      // then - returns the same builder (for chaining)
      assertThat(result).isSameAs(builder);
  
      // and - the documentation child element was added
      final Collection<Documentation> docs =
          builder.getElement().getChildElementsByType(Documentation.class);
      assertThat(docs).hasSize(1);
      assertThat(docs.iterator().next().getTextContent()).isEqualTo("This is a description");
  }

  @Test
  void testDocumentationChaining() {
      // given - documentation should support chaining (multiple calls)
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent("start");
  
      // when
      builder.documentation("First doc").documentation("Second doc");
  
      // then
      final Collection<Documentation> docs =
          builder.getElement().getChildElementsByType(Documentation.class);
      assertThat(docs).hasSize(2);
  }

  @Test
  void testDocumentationWithEmptyContent() {
      // given
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent("start");
  
      // when
      builder.documentation("");
  
      // then
      final Collection<Documentation> docs =
          builder.getElement().getChildElementsByType(Documentation.class);
      assertThat(docs).hasSize(1);
      assertThat(docs.iterator().next().getTextContent()).isEqualTo("");
  }

  @Test
  void testDoneReturnsSameModelInstance() {
      // given
      final StartEventBuilder builder =
          Bpmn.createExecutableProcess("process").startEvent();
  
      // when
      final BpmnModelInstance instanceFromDone = builder.done();
      final BpmnModelInstance instanceFromModelField = builder.modelInstance;
  
      // then
      assertThat(instanceFromDone).isSameAs(instanceFromModelField);
  }

  @Test
  void testSubProcessDoneNavigatesBackToSubProcessElement() {
      // given - nested subprocesses
      final BpmnModelInstance modelInstance =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .subProcess("outer")
              .embeddedSubProcess()
              .startEvent("innerStart")
              .subProcessDone()
              .endEvent("outerEnd")
              .done();
  
      // then - verify the subprocess element is present in the model
      final SubProcess subProcess = modelInstance.getModelElementById("outer");
      assertThat(subProcess).isNotNull();
  }

  @Test
  void testTransactionDoneNavigatesBackToTransactionElement() {
      // given
      final BpmnModelInstance modelInstance =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .transaction("myTx")
              .embeddedSubProcess()
              .startEvent("txStart")
              .transactionDone()
              .endEvent("afterTx")
              .done();
  
      // then
      final Transaction tx = modelInstance.getModelElementById("myTx");
      assertThat(tx).isNotNull();
  }
}