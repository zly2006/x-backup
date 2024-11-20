# X Backup

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

# X Backup

Fabric 的高级备份模组。

## 优势

- ⚡️极速备份：利用多线程技术进行快速备份，以极短的时间完成任务，**速度提高了 50 倍**。 [^1]
- 💾高效利用空间：实现增量备份并自动压缩大文件，最大限度地减少存储使用。
- 🔄无缝回档：在还原后自动重启服务器，实现无缝体验。
- ✨区域回档：仅还原指定范围内的区块，**范围外的玩家不会受到影响**。
- 🛡️全面支持：同时支持服务器和客户端，为您的所有备份需求提供多功能解决方案。
- ☁️自动云备份：轻松将数据备份到云端，支持 Microsoft OneDrive，确保您的信息安全并可以随时访问。

## 如何将语言设置为中文

在 `config/x-backup.config.json` 中将 `language` 设置为 `zh_cn` 即可，或者客户端安装本模组时选择中文。

## 用法

### 命令
```
/xb status
```
显示备份过程的当前状态。

```
/xb create [comment]
```
创建一个带有可选注释的备份。

```
/xb list
```
列出所有备份。

```
/xb restore <id>
```
回档到指定 ID 的备份。

如果你想在回档后停止服务器，可以在末尾加上 `--stop`，这可能会修复一些兼容性问题。

```
/xb restore <id> --chunk <pos1> <pos2>
```

**区域性回档**
回档到指定 ID 的备份，但只回档指定范围内的区块。

```
/xb backup-interval [interval]
```
检查或设置备份间隔（秒）。设置为 0 以禁用自动备份。

```
/xb delete <id>
```
删除指定 ID 的备份。

```
/xb info <id>
```
显示指定 ID 的备份信息。

[^1]: Compared to prime_backup MCDR plugin. 
