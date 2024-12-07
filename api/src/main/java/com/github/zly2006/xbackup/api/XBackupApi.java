package com.github.zly2006.xbackup.api;

import kotlin.LateinitKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipOutputStream;

class Instance {
    static XBackupApi instance;
}

public interface XBackupApi {
    static XBackupApi getInstance() {
        return Instance.instance;
    }

    static XBackupApi setInstance(XBackupApi instance) {
        if (Instance.instance != null && instance != null) {
            throw new IllegalStateException("Instance already set");
        }
        Instance.instance = instance;
        return instance;
    }

    @NotNull Path getBlobFile(@NotNull String hash);

    @Nullable IBackup getBackup(int id);

    boolean check(@NotNull IBackup backup);

    @NotNull List<IBackup> listBackups(int offset, int limit);

    int backupCount();

    void setCloudStorageProvider(@NotNull CloudStorageProvider provider);

    @NotNull CloudStorageProvider getCloudStorageProvider();

    void zipArchive(@NotNull ZipOutputStream stream, @NotNull IBackup backup);

    void restoreBackup(@NotNull IBackup backup, @NotNull Path target);

    void deleteBackup(@NotNull IBackup backup);
}
