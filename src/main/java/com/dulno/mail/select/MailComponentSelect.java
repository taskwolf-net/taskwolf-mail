package com.dulno.mail.select;

import com.dulno.mail.structure.MailDatabaseTable;
import lombok.RequiredArgsConstructor;
import com.dulno.core.user.User;
import com.dulno.workflow.component.input.InputComponentSelect;
import com.dulno.workflow.component.input.InputComponentSelectEntry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public class MailComponentSelect implements InputComponentSelect {
  private final MailDatabaseTable mailDatabaseTable;

  @Override
  public CompletableFuture<List<InputComponentSelectEntry>> compile(
    User user, UUID target, Map<String, String> previousInputs
  ) {
    return mailDatabaseTable.findMailsOfOwner(target)
      .thenApply(mails -> mails.stream()
        .map(mail -> InputComponentSelectEntry.create(mail.id().toString(),
          mail.domain()))
        .toList());
  }
}
