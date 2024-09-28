package com.dulno.mail.trigger.receive;

import com.dulno.core.database.*;
import com.dulno.core.database.condition.DatabaseCondition;
import com.dulno.core.trigger.Trigger;
import com.dulno.core.trigger.TriggerContentDatabaseTable;
import com.dulno.core.trigger.TriggerInformation;
import com.dulno.core.workflow.component.input.InputComponentSelect;
import com.dulno.core.workflow.component.input.InputComponentVariable;
import com.dulno.core.workflow.component.output.OutputComponentVariable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class MailReceiveTrigger implements Trigger {
  public static MailReceiveTrigger create(
    InputComponentSelect mailComponentSelect,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("mailId", DatabaseDataType.UUID));
    return new MailReceiveTrigger(mailComponentSelect,
      TriggerContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "trigger_mail_receive", contentColumns));
  }

  private final InputComponentSelect mailComponentSelect;
  private final TriggerContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "mail-receive-trigger";
  }

  @Override
  public TriggerInformation information() {
    return TriggerInformation.builder()
      .withName("mail.trigger.receive.name")
      .withDescription("mail.trigger.receive.description")
      .withInputVariable(InputComponentVariable.createSelect("mail.trigger.receive.input.mail.name",
        "mailIdentifier", "mail.trigger.receive.input.mail.description", mailComponentSelect))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.receive.output.sender", "mailSender"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.receive.output.prefix", "mailPrefix"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.receive.output.title", "mailTitle"))
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.receive.output.body", "mailBody"))
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
  }

  @Override
  public CompletableFuture<Void> insert(UUID triggerId, Map<String, Object> content) {
    return contentDatabaseTable.insertContent(triggerId,
      DatabaseRow.of(content.get("mailIdentifier")));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Map.of("mailIdentifier", row.findCell(1).stringValue()));
  }

  @Override
  public CompletableFuture<List<UUID>> findEntries(DatabaseCondition condition) {
    return contentDatabaseTable.findContentByCondition(condition).thenApply(
      rows -> rows.stream().map(row -> row.findCell(0).uuidValue()).toList());
  }

  @Override
  public CompletableFuture<Void> delete(UUID triggerId) {
    return contentDatabaseTable.deleteContent(triggerId);
  }
}
