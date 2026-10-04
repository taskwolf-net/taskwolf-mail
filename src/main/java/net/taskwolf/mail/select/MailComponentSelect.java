package net.taskwolf.mail.select;

import net.taskwolf.mail.structure.MailDatabaseTable;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.user.User;
import net.taskwolf.workflow.component.input.InputComponentSelect;
import net.taskwolf.workflow.component.input.InputComponentSelectEntry;

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
