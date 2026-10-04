package net.taskwolf.mail.structure;

import net.taskwolf.core.database.*;
import net.taskwolf.core.database.condition.DatabaseComparison;
import net.taskwolf.core.database.condition.DatabaseCondition;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MailDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "mail";

  public static MailDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("id", DatabaseDataType.UUID,
      DatabaseColumn.Type.PARTITION_KEY));
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID,
      DatabaseColumn.Type.CLUSTERING_KEY));
    columns.add(DatabaseColumn.create("domain", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("mailUser", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("mailPassword", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("smtpHost", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("smtpPort", DatabaseDataType.INT));
    columns.add(DatabaseColumn.create("imapHost", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("imapPort", DatabaseDataType.INT));
    var table = new MailDatabaseTable(connection, keyspace, TABLE_NAME, columns);
    table.createIfNotExists();
    table.createIndexIfNotExists("owner");
    table.initializeViews();
    return table;
  }

  private DatabaseTable ownerDomainView;

  private MailDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  private void initializeViews() {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.TEXT,
      DatabaseColumn.Type.PARTITION_KEY));
    columns.add(DatabaseColumn.create("domain", DatabaseDataType.UUID,
      DatabaseColumn.Type.CLUSTERING_KEY));
    columns.add(DatabaseColumn.create("id", DatabaseDataType.TEXT,
      DatabaseColumn.Type.CLUSTERING_KEY));
    ownerDomainView = createMaterializedViewIfNotExists("owner_domain_view",
      columns);
  }


  public CompletableFuture<Void> insertMail(MailEntry mail) {
    return insertMail(mail.id(), mail.ownerId(), mail.domain(), mail.mailUser(),
      mail.mailPassword(), mail.smtpHost(), mail.smtpPort(), mail.imapHost(),
      mail.imapPort());
  }

  public CompletableFuture<Void> insertMail(
    UUID id, UUID ownerId, String domain, String mailUser, String mailPassword,
    String smtpHost, int smtpPort, String imapHost, int imapPort
  ) {
    return insert(DatabaseRow.of(id, ownerId, domain, mailUser, mailPassword,
      smtpHost, smtpPort, imapHost, imapPort));
  }

  public CompletableFuture<UUID> generateAvailableMailId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    mailExists(id).thenApply(exists -> exists ?
      generateAvailableMailId().thenApply(futureResponse::complete) :
      CompletableFuture.completedFuture(futureResponse.complete(id)));
    return futureResponse;
  }

  public void deleteMail(UUID id) {
    delete(DatabaseCondition.of("id", id));
  }

  public CompletableFuture<Boolean> mailExists(UUID id) {
    return exists(DatabaseCondition.of("id", id));
  }

  public CompletableFuture<Boolean> mailExistsByOwner(UUID ownerId) {
    var condition = DatabaseCondition.of(
      DatabaseComparison.create("owner", ownerId));
    return exists(condition);
  }

  public CompletableFuture<Boolean> mailExists(UUID ownerId, String domain) {
    return ownerDomainView.exists(DatabaseCondition.of(
      DatabaseComparison.create("owner", ownerId),
      DatabaseComparison.create("domain", domain)));
  }

  public CompletableFuture<MailEntry> findMail(UUID id) {
    return selectRow(DatabaseCondition.of("id", id)).thenApply(MailEntry::of);
  }

  public CompletableFuture<List<MailEntry>> findMailsOfOwner(UUID ownerId) {
    var condition = DatabaseCondition.of(
      DatabaseComparison.create("owner", ownerId));
    return selectRows(condition).thenApply(rows ->
      rows.stream().map(MailEntry::of).toList());
  }
}
