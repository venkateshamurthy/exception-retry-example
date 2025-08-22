package io.github.venkateshamurthy.exceptional.examples;

import io.github.resilience4j.core.functions.CheckedSupplier;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.WritableByteChannel;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

@Slf4j
@NoArgsConstructor
public class FileDownloadManager {
    private final ConcurrentMap<String, ReentrantLockWithRefCount> fileLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DownloadTask> downloads = new ConcurrentHashMap<>();
    public static FileDownloadManager getInstance() {return FileDownloadManagerHolder.INSTANCE;}
    private static class FileDownloadManagerHolder {
        private static final FileDownloadManager INSTANCE = new FileDownloadManager();
    }

    public DownloadStatus downloadFile(String fileName, CheckedSupplier<Boolean> downloadAction) throws Throwable {
        WritableByteChannel channel = Channels.newChannel(new FileOutputStream(fileName));
        try {
            channel.write(null);
        } catch(ClosedByInterruptException e) {
            System.out.println("Another thread closed the stream while this one was blocking on I/O!");
        } catch( AsynchronousCloseException ae) {
            System.out.println("Another thread closed the stream while this ");
        }
        log.info("Download request for file: {}", fileName);
        Optional.ofNullable(downloads.get(fileName))
                .map(dt->dt.status).orElse(null);
        DownloadTask existingTask = downloads.get(fileName);
        if (existingTask != null && existingTask.status == DownloadStatus.COMPLETED) {
            return DownloadStatus.COMPLETED;
        }

        ReentrantLockWithRefCount lockWrapper = fileLocks.compute(fileName, (k, v) -> v == null ? new ReentrantLockWithRefCount() : v.increment());
        lockWrapper.lock(); // acquire the per-file lock
        try {
            DownloadTask task = downloads.computeIfAbsent(fileName, k -> new DownloadTask());
            if (task.status == DownloadStatus.COMPLETED) {
                return DownloadStatus.COMPLETED;
            }

            if (task.status == DownloadStatus.NOT_STARTED) {
                task.status = DownloadStatus.IN_PROGRESS;
                try {
                    boolean success = downloadAction.get();
                    task.status = success ? DownloadStatus.COMPLETED : DownloadStatus.FAILED;
                    if (!success) {
                        task.error = new Exception("Download returned false for " + fileName);
                    }
                } catch (Throwable e) {
                    task.status = DownloadStatus.FAILED;
                    task.error = new Exception("Error downloading file " + fileName, e);
                    throw  task.error;
                } finally {
                    task.latch.countDown();
                }
            } else if (task.status == DownloadStatus.IN_PROGRESS) {
                // Another thread is downloading same file, wait
                lockWrapper.unlock(); // Release before waiting
                task.latch.await();
                return task.status;
            }

            return task.status;
        } finally {
            if (lockWrapper.isHeldByCurrentThread()) {
                lockWrapper.unlock();
            }
            // cleanup lock if no longer needed
            fileLocks.computeIfPresent(fileName, (k, v) -> v.decrement());
        }
    }

    public DownloadStatus getStatus(String fileName) {
        DownloadTask task = downloads.get(fileName);
        return task == null ? DownloadStatus.NOT_STARTED : task.status;
    }

    private static class DownloadTask {
        volatile DownloadStatus status = DownloadStatus.NOT_STARTED;
        final CountDownLatch latch = new CountDownLatch(1);
        volatile Throwable error;
    }

    public enum DownloadStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED
    }


    /**
     * Lock with reference count for cleanup.
     */
    private static class ReentrantLockWithRefCount extends java.util.concurrent.locks.ReentrantLock {
        private int refCount = 1;

        ReentrantLockWithRefCount increment() {
            refCount++;
            return this;
        }

        ReentrantLockWithRefCount decrement() {
            refCount--;
            return refCount == 0 ? null : this;
        }
    }
}