# X Backup

[简体中文](https://github.com/zly2006/x-backup/blob/stonecutter/README_zh.md) | English

The advanced backup mod for fabric.

## Advantages

- ⚡️Lightning-Fast Speeds: Utilizes multithreading technology for rapid backup processes, completing tasks in a fraction of the time, **speeding up to 50 times faster**. [^1]
- 💾Space-Efficient: Implements incremental backups and automatically compresses large files, minimizing storage usage.
- 🔄Seamless Restoring: Automatically restarts the server after restoring, allowing for a seamless experience.
- ✨Regional Restoring: Restore a region file (`--mca`) or only the selected chunks (`--chunk`). **Players outside the restored range will not be affected**.
- 🛡️Flexible Support: Designed to support both servers and clients, providing a versatile solution for all your backup needs.
- ☁️Automatic Cloud Backup: Effortlessly back up your data to the cloud, with support for Microsoft OneDrive, ensuring your information is safe and accessible from anywhere.

## Usage

### Creating a Backup

You can use the `/xb create` command to create a new backup. This command saves the current state of the game so you can restore it later. If you want to add a note to the backup, you can add it after the command, for example:

```
/xb create This is my first backup
```

This way, you create a backup with a note.

### Viewing Created Backups

If you want to view the backups you have created, you can use the `/xb list` command. This command lists all the backups and displays the number, creation time, and note of each backup. For example:

```
/xb list
```

This command displays the last 6 backups. If you have many backups, you can click the gray font button to view more backups.

### Restoring a Backup

If you want to restore to a specific backup, you can use the `/xb restore <id>` command, where `<id>` is the number of the backup. For example:

```
/xb restore 1
```

This command restores the game to the state of backup number 1.

To restore only part of the current dimension, use `--mca` (whole region files) or `--chunk` (individual chunks). Coordinates default to MCA region indices or chunk indices. Add `--block` if you want to pass block X/Z coordinates instead.

```
/xb restore 1 --mca 0 0 0 0
```

This restores only `r.0.0.mca` in the current dimension.

```
/xb restore 1 --chunk 0 0 10 10
```

This restores chunks (0, 0) through (10, 10). Chunks outside that range are left unchanged.

```
/xb restore 1 --mca --block 0 0 10 10
```

This treats the numbers as block coordinates, converts them to MCA region files, then restores those files.

```
/xb restore 1 --chunk --block 0 0 10 10
```

This treats the numbers as block coordinates, converts them to chunk coordinates, then restores only those chunks.

### Scheduled Backup Configuration

You can set the automatic backup interval using the `/xb backup-interval <seconds>` command. For example, if you want to automatically back up every 3 hours, you can set it like this:

```
/xb backup-interval 10800
```

This command sets the automatic backup interval to 10,800 seconds (i.e., 3 hours).

### Mirror Server Configuration

If you are using a mirror server, you can use the `/mirror` command to synchronize the latest backup from the main server. For example:

First, you need to create a backup on the main server:

```
/xb create
```

Then, in the mirror server configuration, set the main server's file path (the one containing `server.properties`) and `blob_path`, and enable mirror mode:

```json
{
  "mirror_mode": true,   
  "mirror_from": "C:\\MinecraftServer\\My-Server",
  "blob_path": "C:\\MinecraftServer\\My-Server\\blob"
}
```

After that, execute the `/mirror` command. This command will synchronize the latest backup state from the main server to the mirror server. You can also add the `id` parameter to specify the backup number to synchronize:

```
/mirror 1
```

If you want to stop the server and restore the backup, you can use the `--stop` parameter:

```
/mirror --stop
```

### Cleaning Up Unnecessary Backups

The mod also provides an automatic cleanup feature for old backups. You can set the `keep_policy` in the configuration file, for example:

```json
{
  "prune": {
    "enabled": false,
    "keep_policy": {
      "1d": "30m",
      "1w": "6h",
      "1M": "1d",
      "1y": "1w",
      "2y": "1M"
    }
  }
}
```

This configuration means:
- Keep backups every 30 minutes for the last 1 day
- Keep backups every 6 hours for the last 1 week
- Keep daily backups for the last 1 month
- Keep weekly backups for the last 1 year
- Keep monthly backups for the last 2 years

You can adjust these settings according to your needs.

When `enabled` is `true`, the mod will automatically clean up backups that do not meet the retention policy. Alternatively, you can manually clean up unnecessary backups using the `/xb prune` command.

### Viewing Backup Information

If you want to view detailed information about a specific backup, you can use the `/xb info <id>` command, for example:

```
/xb info 1
```

This command will display detailed information about backup #1, including backup time, notes, size, etc.

## Credits

Some part (GUI) of this mod is based on BackupManager by CreeperHost LTD.
