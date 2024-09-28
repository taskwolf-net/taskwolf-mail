package com.dulno.mail;

import com.dulno.mail.structure.MailDatabaseTable;
import lombok.RequiredArgsConstructor;
import com.dulno.core.account.AccountLink;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class MailAccountLink implements AccountLink {
  private final MailDatabaseTable mailDatabaseTable;

  @Override
  public CompletableFuture<Boolean> accountExists(UUID id) {
    return mailDatabaseTable.mailExistsByOwner(id);
  }

  @Override
  public CompletableFuture<List<String>> findAccounts(UUID id) {
    return mailDatabaseTable.findMailsOfOwner(id).thenApply(mails ->
      mails.stream().map(mail -> mail.id().toString()).toList());
  }

  @Override
  public void removeAccount(UUID id, String identifier) {
    mailDatabaseTable.deleteMail(UUID.fromString(identifier));
  }

  @Override
  public String registrationUrl(UUID id, String apiKey) {
    return "https://dulno.com/mail/connect/";
  }

  @Override
  public String description() {
    return "mail.link.description";
  }
}