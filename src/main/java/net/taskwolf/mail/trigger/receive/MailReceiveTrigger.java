package net.taskwolf.mail.trigger.receive;

import net.taskwolf.core.database.*;
import net.taskwolf.core.database.condition.DatabaseCondition;
import net.taskwolf.mail.structure.MailDatabaseTable;
import net.taskwolf.workflow.trigger.Trigger;
import net.taskwolf.workflow.trigger.TriggerContentDatabaseTable;
import net.taskwolf.workflow.trigger.TriggerInformation;
import net.taskwolf.workflow.component.input.InputComponentSelect;
import net.taskwolf.workflow.component.input.InputComponentVariable;
import net.taskwolf.workflow.component.output.OutputComponentVariable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class MailReceiveTrigger implements Trigger {
  public static MailReceiveTrigger create(
    MailDatabaseTable mailDatabaseTable, InputComponentSelect mailComponentSelect,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("ownerId", DatabaseDataType.UUID));
    contentColumns.add(DatabaseColumn.create("mailId", DatabaseDataType.UUID));
    return new MailReceiveTrigger(mailDatabaseTable, mailComponentSelect,
      TriggerContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "trigger_mail_receive", contentColumns));
  }

  private final MailDatabaseTable mailDatabaseTable;
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
      .withOutputVariable(OutputComponentVariable.create("mail.trigger.receive.output.identifier", "mailEntryIdentifier"))
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
  public CompletableFuture<Void> insert(
    UUID triggerId, UUID ownerId, Map<String, Object> content
  ) {
    return contentDatabaseTable.insertContent(triggerId, DatabaseRow.of(ownerId,
      UUID.fromString((String) content.get("mailIdentifier"))));
  }

  @Override
  public CompletableFuture<Boolean> checkExecution(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId)
      .thenCompose(row -> mailDatabaseTable.mailExists(row.findCell(2).uuidValue())
        .thenCompose(exists -> checkExecution(row.findCell(1).uuidValue(),
          row.findCell(2).uuidValue(), exists)));
  }

  public CompletableFuture<Boolean> checkExecution(
    UUID ownerId, UUID mailId, boolean mailExists
  ) {
    if (!mailExists) {
      return CompletableFuture.completedFuture(false);
    }
    return mailDatabaseTable.findMail(mailId)
      .thenApply(mail -> mail.ownerId().equals(ownerId));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Map.of("mailIdentifier", row.findCell(2).uuidValue().toString()));
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
