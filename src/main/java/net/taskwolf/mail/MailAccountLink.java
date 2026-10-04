package net.taskwolf.mail;

import net.taskwolf.core.account.AccountLinkEntry;
import net.taskwolf.mail.structure.MailDatabaseTable;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.account.AccountLink;

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
  public CompletableFuture<List<AccountLinkEntry>> findAccounts(UUID id) {
    return mailDatabaseTable.findMailsOfOwner(id)
      .thenApply(mails -> mails.stream()
        .map(mail -> AccountLinkEntry.create(mail.id().toString(), mail.domain()))
        .toList());
  }

  @Override
  public void removeAccount(UUID id, String identifier) {
    mailDatabaseTable.deleteMail(UUID.fromString(identifier));
  }

  @Override
  public String registrationUrl(UUID id, String apiKey) {
    return "/mail/connect/";
  }

  @Override
  public String description() {
    return "mail.link.description";
  }
}