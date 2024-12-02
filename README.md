# X Backup

[简体中文](README_zh.md) | English

The advanced backup mod for fabric.

## Advantages

- ⚡️Lightning-Fast Speeds: Utilizes multithreading technology for rapid backup processes, completing tasks in a fraction of the time, **speeding up to 50 times faster**. [^1]
- 💾Space-Efficient: Implements incremental backups and automatically compresses large files, minimizing storage usage.
- 🔄Seamless Restoring: Automatically restarts the server after restoring, allowing for a seamless experience.
- ✨Regional Restoring: Restore only the chunks within a specified range, **Players outside the range will not be affected**.
- 🛡️Flexible Support: Designed to support both servers and clients, providing a versatile solution for all your backup needs.
- ☁️Automatic Cloud Backup: Effortlessly back up your data to the cloud, with support for Microsoft OneDrive, ensuring your information is safe and accessible from anywhere.

## Usage

### Commands
```
/xb status
```
Displays the current status of the backup process.

```
/xb create [comment]
```
Creates a backup with an optional comment.

```
/xb list
```
Lists all backups.

```
/xb restore <id>
```
Restores a backup with the specified ID.

You can add `--stop` at the end if you want to stop the server after restoring, this may fix some compatibility issues.

```
/xb restore <id> --chunk <pos1> <pos2>
```

**Regionally restore**
Restores a backup with the specified ID, but only restores the chunks within the specified range.

```
/xb backup-interval [interval]
```
Checks or sets the backup interval in seconds. Set to 0 to disable automatic backups.

```
/xb delete <id>
```
Deletes a backup with the specified ID.

```
/xb info <id>
```
Displays information about a backup with the specified ID.
