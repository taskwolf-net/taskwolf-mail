package net.taskwolf.mail.structure;

import net.taskwolf.core.database.DatabaseRow;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(staticName = "create")
public final class MailEntry {
  public static MailEntry of(DatabaseRow row) {
    return create(row.findCell(0).uuidValue(), row.findCell(1).uuidValue(),
      row.findCell(2).stringValue(), row.findCell(3).stringValue(),
      row.findCell(4).stringValue(), row.findCell(5).stringValue(),
      row.findCell(6).integerValue(), row.findCell(7).stringValue(),
      row.findCell(8).integerValue());
  }

  private final UUID id;
  private final UUID ownerId;
  private final String domain;
  private final String mailUser;
  private final String mailPassword;
  private final String smtpHost;
  private final int smtpPort;
  private final String imapHost;
  private final int imapPort;
}
