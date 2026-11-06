/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "dictionary/utils/file_utils.h"

#include <cstdio>
#include <cerrno>
#include <cstdint>
#include <climits>
#include <memory>
#include <string>
#include <vector>
#include "dictionary/utils/dict_file_writing_utils.h"
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <libgen.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

namespace latinime {

// Returns -1 on error.
/* static */ int FileUtils::getFileSize(const char *const filePath) {
    const int fd = open(filePath, O_RDONLY);
    if (fd == -1) {
        return -1;
    }
    struct stat statBuf;
    if (fstat(fd, &statBuf) != 0) {
        close(fd);
        return -1;
    }
    close(fd);
    return static_cast<int>(statBuf.st_size);
}

/* static */ bool FileUtils::existsDir(const char *const dirPath) {
    DIR *const dir = opendir(dirPath);
    if (dir == NULL) {
        return false;
    }
    closedir(dir);
    return true;
}

namespace {
bool removeTree(const std::string &root) {
    struct Frame { std::string path; int depth; bool visited; uint64_t device; uint64_t inode; };
    std::vector<Frame> pending{{root, 0, false, 0, 0}};
    size_t entries = 0;
    while (!pending.empty()) {
        Frame frame = std::move(pending.back()); pending.pop_back();
        if (frame.path.size() >= PATH_MAX || frame.depth > 64) { errno = EOVERFLOW; return false; }
        struct stat status;
        if (lstat(frame.path.c_str(), &status) != 0) {
            if (errno == ENOENT) continue;
            return false;
        }
        if (!S_ISDIR(status.st_mode)) {
            if (frame.depth == 0) { errno = ENOTDIR; return false; }
            if (unlink(frame.path.c_str()) != 0 && errno != ENOENT) return false;
            continue;
        }
        if (frame.visited) {
            if (status.st_dev != frame.device || status.st_ino != frame.inode) { errno = EBUSY; return false; }
            if (rmdir(frame.path.c_str()) != 0 && errno != ENOENT) return false;
            continue;
        }
        std::unique_ptr<DIR, int(*)(DIR*)> dir(opendir(frame.path.c_str()), closedir);
        if (!dir) return false;
        struct stat opened;
        if (fstat(dirfd(dir.get()), &opened) != 0 || opened.st_dev != status.st_dev
                || opened.st_ino != status.st_ino) { errno = EBUSY; return false; }
        pending.push_back({frame.path, frame.depth, true, status.st_dev, status.st_ino});
        errno = 0;
        while (auto entry = readdir(dir.get())) {
            if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
            if (++entries > 4096) { errno = EOVERFLOW; return false; }
            pending.push_back({frame.path + "/" + entry->d_name, frame.depth + 1, false, 0, 0});
        }
        if (errno != 0) return false;
    }
    return true;
}
}

/* static */ bool FileUtils::removeDirAndFiles(const char *const path) {
    std::lock_guard<std::recursive_mutex> lock(DictFileWritingUtils::fileMutex());
    if (!path || !*path || strnlen(path, PATH_MAX) >= PATH_MAX) return false;
    for (int attempt = 0; attempt < 6; ++attempt) {
        if (removeTree(path)) return true;
        if (errno != ENOTEMPTY && errno != EBUSY) return false;
    }
    return false;
}

/* static */ int FileUtils::getFilePathWithSuffixBufSize(const char *const filePath,
        const char *const suffix) {
    return strlen(filePath) + strlen(suffix) + 1 /* terminator */;
}

/* static */ void FileUtils::getFilePathWithSuffix(const char *const filePath,
        const char *const suffix, const int filePathBufSize, char *const outFilePath) {
    snprintf(outFilePath, filePathBufSize, "%s%s", filePath, suffix);
}

/* static */ int FileUtils::getFilePathBufSize(const char *const dirPath,
        const char *const fileName) {
    return strlen(dirPath) + 1 /* '/' */ + strlen(fileName) + 1 /* terminator */;
}

/* static */ void FileUtils::getFilePath(const char *const dirPath, const char *const fileName,
        const int filePathBufSize, char *const outFilePath) {
    snprintf(outFilePath, filePathBufSize, "%s/%s", dirPath, fileName);
}

/* static */ bool FileUtils::getFilePathWithoutSuffix(const char *const filePath,
        const char *const suffix, const int outDirPathBufSize, char *const outDirPath) {
    const int filePathLength = strlen(filePath);
    const int suffixLength = strlen(suffix);
    if (filePathLength <= suffixLength) {
        AKLOGE("File path length (%s:%d) is shorter that suffix length (%s:%d).",
                filePath, filePathLength, suffix, suffixLength);
        return false;
    }
    const int resultFilePathLength = filePathLength - suffixLength;
    if (outDirPathBufSize <= resultFilePathLength) {
        AKLOGE("outDirPathBufSize is too small. filePath: %s, suffix: %s, outDirPathBufSize: %d",
                filePath, suffix, outDirPathBufSize);
        return false;
    }
    if (strncmp(filePath + resultFilePathLength, suffix, suffixLength) != 0) {
        AKLOGE("File Path %s does not have %s as a suffix", filePath, suffix);
        return false;
    }
    snprintf(outDirPath, resultFilePathLength + 1 /* terminator */, "%s", filePath);
    return true;
}

/* static */ void FileUtils::getDirPath(const char *const filePath, const int outDirPathBufSize,
        char *const outDirPath) {
    for (int i = strlen(filePath) - 1; i >= 0; --i) {
        if (filePath[i] == '/') {
            if (i >= outDirPathBufSize) {
                AKLOGE("outDirPathBufSize is too small. filePath: %s, outDirPathBufSize: %d",
                        filePath, outDirPathBufSize);
                ASSERT(false);
                return;
            }
            snprintf(outDirPath, i + 1 /* terminator */, "%s", filePath);
            return;
        }
    }
}

/* static */ void FileUtils::getBasename(const char *const filePath,
        const int outNameBufSize, char *const outName) {
    if (!outName || outNameBufSize <= 0) return;
    outName[0] = 0;
    if (!filePath || strnlen(filePath, PATH_MAX) >= PATH_MAX) return;
    std::string path(filePath);
    while (path.size() > 1 && path.back() == '/') path.pop_back();
    const auto slash = path.find_last_of('/');
    const std::string base = path == "/" ? "/" : path.substr(slash == std::string::npos ? 0 : slash + 1);
    if (base.size() >= static_cast<size_t>(outNameBufSize)) return;
    memcpy(outName, base.c_str(), base.size() + 1);
}

} // namespace latinime
