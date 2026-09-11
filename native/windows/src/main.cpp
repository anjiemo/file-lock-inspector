// Copyright 2026 File Lock Inspector contributors. SPDX-License-Identifier: Apache-2.0
#include <windows.h>
#include <winternl.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <iostream>
#include <vector>
#include <string>
#include <set>
#include <memory>
#include <chrono>
#include <sstream>
#include <iomanip>
#include "../vendor/nlohmann/json.hpp"

using json = nlohmann::json;
using NtQuerySystemInformationFn = NTSTATUS (NTAPI*)(ULONG, PVOID, ULONG, PULONG);
struct HandleEntry {
    PVOID object; ULONG_PTR pid; ULONG_PTR handle; ULONG access;
    USHORT trace; USHORT type; ULONG attributes; ULONG reserved;
};
struct HandleTable { ULONG_PTR count; ULONG_PTR reserved; HandleEntry entries[1]; };
class Handle {
    HANDLE value_ = nullptr;
public:
    explicit Handle(HANDLE value = nullptr) : value_(value) {}
    ~Handle() { if (valid()) CloseHandle(value_); }
    Handle(const Handle&) = delete;
    Handle& operator=(const Handle&) = delete;
    bool valid() const { return value_ && value_ != INVALID_HANDLE_VALUE; }
    HANDLE get() const { return value_; }
};

std::string utf8(const std::wstring& text) {
    if (text.empty()) return {};
    int size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, text.data(), (int)text.size(), nullptr, 0, nullptr, nullptr);
    if (size <= 0) return {};
    std::string result(size, '\0');
    WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, text.data(), (int)text.size(), result.data(), size, nullptr, nullptr);
    return result;
}
void emit(json value) { value["protocol"] = 1; std::cout << value.dump(-1, ' ', false, json::error_handler_t::replace) << std::endl; }
uint64_t startTime(HANDLE process) {
    FILETIME creation{}, exit{}, kernel{}, user{};
    if (!GetProcessTimes(process, &creation, &exit, &kernel, &user)) return 0;
    return (uint64_t(creation.dwHighDateTime) << 32) | creation.dwLowDateTime;
}
std::wstring executable(HANDLE process) {
    std::wstring result(32768, L'\0'); DWORD size = (DWORD)result.size();
    if (!QueryFullProcessImageNameW(process, 0, result.data(), &size)) return {};
    result.resize(size); return result;
}
DWORD parentPid() {
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
    PROCESSENTRY32W entry{}; entry.dwSize = sizeof(entry);
    if (snapshot.valid() && Process32FirstW(snapshot.get(), &entry)) do {
        if (entry.th32ProcessID == GetCurrentProcessId()) return entry.th32ParentProcessID;
    } while (Process32NextW(snapshot.get(), &entry));
    return 0;
}
std::string owner(HANDLE process) {
    HANDLE raw = nullptr;
    if (!OpenProcessToken(process, TOKEN_QUERY, &raw)) return {};
    Handle token(raw); DWORD size = 0;
    GetTokenInformation(token.get(), TokenUser, nullptr, 0, &size);
    if (!size) return {};
    std::vector<BYTE> buffer(size);
    if (!GetTokenInformation(token.get(), TokenUser, buffer.data(), size, &size)) return {};
    wchar_t name[512]{}, domain[512]{}; DWORD nameSize = 512, domainSize = 512; SID_NAME_USE use{};
    if (!LookupAccountSidW(nullptr, reinterpret_cast<TOKEN_USER*>(buffer.data())->User.Sid, name, &nameSize, domain, &domainSize, &use)) return {};
    return utf8(std::wstring(domain) + L"\\" + name);
}
struct Services {
    std::set<DWORD> pids;
    bool complete = false;
    Services() {
        SC_HANDLE manager = OpenSCManagerW(nullptr, nullptr, SC_MANAGER_ENUMERATE_SERVICE);
        if (!manager) return;
        DWORD needed = 0, count = 0, resume = 0;
        std::vector<BYTE> buffer(256 * 1024);
        BOOL ok = EnumServicesStatusExW(manager, SC_ENUM_PROCESS_INFO, SERVICE_WIN32, SERVICE_STATE_ALL,
            buffer.data(), (DWORD)buffer.size(), &needed, &count, &resume, nullptr);
        if (ok) {
            auto entries = reinterpret_cast<ENUM_SERVICE_STATUS_PROCESSW*>(buffer.data());
            for (DWORD i = 0; i < count; ++i) if (entries[i].ServiceStatusProcess.dwProcessId) pids.insert(entries[i].ServiceStatusProcess.dwProcessId);
            complete = true;
        }
        CloseServiceHandle(manager);
    }
};
std::string protection(HANDLE process, DWORD pid, DWORD idePid, const Services& services) {
    if (pid == GetCurrentProcessId() || pid == parentPid() || pid == idePid) return "protected";
    if (pid <= 4 || services.pids.contains(pid)) return "protected";
    const auto image = executable(process);
    const auto name = image.substr(image.find_last_of(L"\\/") + 1);
    if (_wcsicmp(name.c_str(), L"file-lock-inspector-helper.exe") == 0) return "protected";
    BOOL critical = FALSE; DWORD session = 0;
    if (!services.complete || !IsProcessCritical(process, &critical) || !ProcessIdToSessionId(pid, &session)) return "unknownProtection";
    if (critical || session == 0) return "protected";
    if (!startTime(process)) return "identityUnavailable";
    return {};
}
struct FileIdentity {
    FILE_ID_INFO info{};
    bool valid = false;
    explicit FileIdentity(HANDLE file) { valid = GetFileInformationByHandleEx(file, FileIdInfo, &info, sizeof(info)); }
    std::string key() const {
        std::ostringstream value; value << std::hex << info.VolumeSerialNumber << ':';
        for (BYTE byte : info.FileId.Identifier) value << std::setw(2) << std::setfill('0') << (unsigned)byte;
        return value.str();
    }
    bool matches(HANDLE file) const {
        FileIdentity other(file);
        return valid && other.valid && info.VolumeSerialNumber == other.info.VolumeSerialNumber &&
            memcmp(info.FileId.Identifier, other.info.FileId.Identifier, sizeof(info.FileId.Identifier)) == 0;
    }
};
struct Scan {
    std::wstring path;
    DWORD idePid;
    DWORD onlyPid;
    std::set<DWORD> found;
    unsigned skipped = 0;
    bool success = false;
    bool publish;
    Services services;
    std::string fileId;
    std::string expectedFileId;
    void report(DWORD pid, const char* evidence) {
        if (pid == GetCurrentProcessId() || found.contains(pid)) return;
        found.insert(pid);
        if (!publish) return;
        Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid));
        std::wstring fullPath = process.valid() ? executable(process.get()) : L"";
        auto slash = fullPath.find_last_of(L"\\/");
        auto name = slash == std::wstring::npos ? fullPath : fullPath.substr(slash + 1);
        std::string reason = process.valid() ? protection(process.get(), pid, idePid, services) : "accessDenied";
        emit({{"type", "process"}, {"pid", pid}, {"startTime", process.valid() ? std::to_string(startTime(process.get())) : "0"},
            {"name", utf8(name)}, {"executable", utf8(fullPath)}, {"owner", process.valid() ? owner(process.get()) : ""},
            {"file", utf8(path)}, {"fileId", fileId}, {"evidence", evidence}, {"blockedReason", reason}, {"canTerminate", reason.empty()}});
    }
    void run() {
        Handle target(CreateFileW(path.c_str(), 0, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, nullptr,
            OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, nullptr));
        if (!target.valid()) {
            DWORD error = GetLastError();
            if (publish) emit({{"type", "end"}, {"status", error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND ? "missingFile" : "accessDenied"}, {"error", error}});
            return;
        }
        BY_HANDLE_FILE_INFORMATION basic{};
        if (!GetFileInformationByHandle(target.get(), &basic) || (basic.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) {
            if (publish) emit({{"type", "end"}, {"status", "invalidPath"}});
            return;
        }
        FileIdentity identity(target.get());
        if (!identity.valid) {
            if (publish) emit({{"type", "end"}, {"status", "identityUnavailable"}});
            return;
        }
        fileId = identity.key();
        if (!expectedFileId.empty() && expectedFileId != fileId) return;
        auto query = reinterpret_cast<NtQuerySystemInformationFn>(GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "NtQuerySystemInformation"));
        if (!query) { if (publish) emit({{"type", "end"}, {"status", "unsupported"}}); return; }
        std::vector<BYTE> buffer(1024 * 1024); ULONG needed = 0; NTSTATUS status;
        while ((status = query(64, buffer.data(), (ULONG)buffer.size(), &needed)) == (NTSTATUS)0xC0000004L && buffer.size() < 256 * 1024 * 1024) {
            buffer.resize(std::max(buffer.size() * 2, (size_t)needed + 4096));
        }
        if (status < 0 || buffer.size() < sizeof(HandleTable)) {
            if (publish) emit({{"type", "end"}, {"status", "failed"}, {"error", (long)status}});
            return;
        }
        auto table = reinterpret_cast<HandleTable*>(buffer.data());
        size_t maxCount = (buffer.size() - offsetof(HandleTable, entries)) / sizeof(HandleEntry);
        if (table->count > maxCount) { if (publish) emit({{"type", "end"}, {"status", "failed"}}); return; }
        // Learn the File object type from our own target handle, rather than relying on an OS-specific index.
        USHORT fileType = 0;
        for (size_t i = 0; i < table->count; ++i) {
            auto& entry = table->entries[i];
            if (entry.pid == GetCurrentProcessId() && entry.handle == reinterpret_cast<ULONG_PTR>(target.get())) { fileType = entry.type; break; }
        }
        if (!fileType) { if (publish) emit({{"type", "end"}, {"status", "failed"}}); return; }
        DWORD currentPid = 0; std::unique_ptr<Handle> process;
        for (size_t i = 0; i < table->count; ++i) {
            auto& entry = table->entries[i];
            DWORD pid = (DWORD)entry.pid;
            if (entry.type != fileType || pid == GetCurrentProcessId() || (onlyPid && pid != onlyPid) || found.contains(pid)) continue;
            if (!process || currentPid != pid) {
                currentPid = pid; process = std::make_unique<Handle>(OpenProcess(PROCESS_DUP_HANDLE, FALSE, pid));
            }
            if (!process->valid()) { ++skipped; continue; }
            HANDLE copy = nullptr;
            if (!DuplicateHandle(process->get(), reinterpret_cast<HANDLE>(entry.handle), GetCurrentProcess(), &copy, 0, FALSE, DUPLICATE_SAME_ACCESS)) { ++skipped; continue; }
            Handle duplicate(copy);
            // The parent applies a deadline: kernel file queries can block in third-party file-system drivers.
            if (GetFileType(copy) == FILE_TYPE_DISK && identity.matches(copy)) report(pid, "handle");
        }
        Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
        PROCESSENTRY32W entry{}; entry.dwSize = sizeof(entry);
        if (!snapshot.valid() || !Process32FirstW(snapshot.get(), &entry)) ++skipped;
        else do {
            DWORD pid = entry.th32ProcessID;
            if (!pid || pid == GetCurrentProcessId() || (onlyPid && pid != onlyPid) || found.contains(pid)) continue;
            Handle proc(OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid));
            if (!proc.valid()) { ++skipped; continue; }
            DWORD bytes = 0; std::vector<HMODULE> modules(256);
            if (!EnumProcessModulesEx(proc.get(), modules.data(), (DWORD)(modules.size() * sizeof(HMODULE)), &bytes, LIST_MODULES_ALL)) { ++skipped; continue; }
            if (bytes > modules.size() * sizeof(HMODULE)) {
                modules.resize(bytes / sizeof(HMODULE));
                if (!EnumProcessModulesEx(proc.get(), modules.data(), bytes, &bytes, LIST_MODULES_ALL)) { ++skipped; continue; }
            }
            for (size_t i = 0; i < std::min(modules.size(), (size_t)bytes / sizeof(HMODULE)); ++i) {
                std::wstring name(32768, L'\0'); DWORD length = GetModuleFileNameExW(proc.get(), modules[i], name.data(), (DWORD)name.size());
                if (!length || length >= name.size()) { ++skipped; continue; }
                name.resize(length);
                Handle moduleFile(CreateFileW(name.c_str(), 0, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, nullptr, OPEN_EXISTING, 0, nullptr));
                if (moduleFile.valid() && identity.matches(moduleFile.get())) { report(pid, "module"); break; }
            }
        } while (Process32NextW(snapshot.get(), &entry));
        success = true;
        if (publish) emit({{"type", "end"}, {"status", skipped ? "partial" : "complete"}, {"skipped", skipped}});
    }
};

int wmain(int argc, wchar_t** argv) {
    try {
        if (argc == 2 && std::wstring(argv[1]) == L"--version") { emit({{"type", "version"}, {"version", "0.1.0"}}); return 0; }
        if (argc < 4) { emit({{"type", "end"}, {"status", "invalidArguments"}}); return 2; }
        std::wstring command = argv[1], path = argv[2]; DWORD idePid = std::stoul(argv[3]);
        auto drivePath = path.rfind(L"\\\\?\\", 0) == 0 ? path.substr(4) : path;
        if (drivePath.size() < 3 || drivePath[1] != L':' || drivePath.find(L':', 2) != std::wstring::npos || (drivePath[2] != L'\\' && drivePath[2] != L'/') ||
            !((drivePath[0] >= L'A' && drivePath[0] <= L'Z') || (drivePath[0] >= L'a' && drivePath[0] <= L'z'))) {
            emit({{"type", "end"}, {"status", "invalidPath"}}); return 2;
        }
        std::wstring root = drivePath.substr(0, 2) + L"\\";
        if (GetDriveTypeW(root.c_str()) == DRIVE_REMOTE) { emit({{"type", "end"}, {"status", "unsupported"}}); return 2; }
        if (command == L"scan") { Scan scan{path, idePid, 0, {}, 0, false, true}; scan.run(); return scan.success ? 0 : 1; }
        if (command == L"terminate" && argc == 7) {
            DWORD pid = std::stoul(argv[4]); uint64_t expected = std::stoull(argv[5]);
            Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_TERMINATE | SYNCHRONIZE, FALSE, pid));
            auto finish = [&](std::string result) { emit({{"type", "operation"}, {"pid", pid}, {"status", result}}); return result == "terminated" ? 0 : 1; };
            if (!process.valid()) return finish(GetLastError() == ERROR_INVALID_PARAMETER ? "exited" : "accessDenied");
            if (!expected || startTime(process.get()) != expected) return finish("identityChanged");
            Services services; auto reason = protection(process.get(), pid, idePid, services);
            if (!reason.empty()) return finish(reason);
            Scan scan{path, idePid, pid, {}, 0, false, false}; scan.expectedFileId = utf8(argv[6]);
            if (scan.expectedFileId.empty()) return finish("notConfirmed");
            scan.run();
            if (!scan.success || !scan.found.contains(pid)) return finish("notConfirmed");
            if (WaitForSingleObject(process.get(), 0) == WAIT_OBJECT_0) return finish("exited");
            if (!TerminateProcess(process.get(), 1)) return finish("accessDenied");
            return finish(WaitForSingleObject(process.get(), 5000) == WAIT_OBJECT_0 ? "terminated" : "exitTimeout");
        }
        emit({{"type", "end"}, {"status", "invalidArguments"}}); return 2;
    } catch (const std::exception&) { emit({{"type", "end"}, {"status", "failed"}}); return 1; }
}
