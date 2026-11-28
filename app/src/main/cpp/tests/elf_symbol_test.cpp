#include "utilities/elf.h"
#include <elf.h>
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <limits>
#include <string>
#include <vector>
#include <unistd.h>

namespace {
constexpr const char* function_name = "test_path_check";
constexpr size_t note_offset = 0x180;
constexpr size_t symbol_offset = 0x300;
constexpr size_t string_offset = 0x400;
constexpr size_t section_offset = 0x500;
constexpr size_t code_offset = 0x200;
constexpr uint64_t code_address = 0x1200;

template<class T> void write(std::vector<uint8_t>& bytes, size_t offset, const T& value) {
    assert(offset + sizeof(T) <= bytes.size());
    std::memcpy(bytes.data() + offset, &value, sizeof(T));
}

template<class T> T read(const std::vector<uint8_t>& bytes, size_t offset) {
    T value{};
    assert(offset + sizeof(T) <= bytes.size());
    std::memcpy(&value, bytes.data() + offset, sizeof(T));
    return value;
}

std::vector<uint8_t> fixture() {
    std::vector<uint8_t> bytes(0x800);
    Elf64_Ehdr header{};
    std::memcpy(header.e_ident, ELFMAG, SELFMAG);
    header.e_ident[EI_CLASS] = ELFCLASS64;
    header.e_ident[EI_DATA] = ELFDATA2LSB;
    header.e_ident[EI_VERSION] = EV_CURRENT;
    header.e_type = ET_DYN;
    header.e_machine = EM_AARCH64;
    header.e_version = EV_CURRENT;
    header.e_ehsize = sizeof(header);
    header.e_phoff = sizeof(header);
    header.e_phentsize = sizeof(Elf64_Phdr);
    header.e_phnum = 2;
    header.e_shoff = section_offset;
    header.e_shentsize = sizeof(Elf64_Shdr);
    header.e_shnum = 5;
    write(bytes, 0, header);

    Elf64_Phdr segment{};
    segment.p_type = PT_LOAD;
    segment.p_flags = PF_R | PF_X;
    segment.p_offset = code_offset;
    segment.p_vaddr = code_address;
    segment.p_filesz = segment.p_memsz = 0x40;
    write(bytes, sizeof(header), segment);
    segment = {};
    segment.p_type = PT_NOTE;
    segment.p_offset = note_offset;
    segment.p_filesz = 24;
    write(bytes, sizeof(header) + sizeof(segment), segment);
    Elf64_Nhdr note{4, 8, NT_GNU_BUILD_ID};
    write(bytes, note_offset, note);
    std::memcpy(bytes.data() + note_offset + sizeof(note), "GNU\0test-id!", 12);

    Elf64_Shdr section{};
    section.sh_type = SHT_PROGBITS;
    section.sh_flags = SHF_ALLOC | SHF_EXECINSTR;
    section.sh_addr = code_address;
    section.sh_offset = code_offset;
    section.sh_size = 0x40;
    write(bytes, section_offset + sizeof(section), section);
    section = {};
    section.sh_type = SHT_NOTE;
    section.sh_offset = note_offset;
    section.sh_size = 24;
    write(bytes, section_offset + 2 * sizeof(section), section);
    section = {};
    section.sh_type = SHT_SYMTAB;
    section.sh_offset = symbol_offset;
    section.sh_size = 3 * sizeof(Elf64_Sym);
    section.sh_entsize = sizeof(Elf64_Sym);
    section.sh_link = 4;
    write(bytes, section_offset + 3 * sizeof(section), section);
    section = {};
    section.sh_type = SHT_STRTAB;
    section.sh_offset = string_offset;
    section.sh_size = std::strlen(function_name) + 2;
    write(bytes, section_offset + 4 * sizeof(section), section);
    std::memcpy(bytes.data() + string_offset + 1, function_name, std::strlen(function_name) + 1);

    Elf64_Sym symbol{};
    symbol.st_name = 1;
    symbol.st_info = ELF64_ST_INFO(STB_LOCAL, STT_FUNC);
    symbol.st_shndx = 1;
    symbol.st_value = code_address;
    symbol.st_size = 16;
    write(bytes, symbol_offset + sizeof(symbol), symbol);
    return bytes;
}

void save(const std::string& path, const std::vector<uint8_t>& bytes) {
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    file.write(reinterpret_cast<const char*>(bytes.data()), bytes.size());
    file.close();
    assert(file.good());
}
} // namespace

int main(int argc, char** argv) {
    char temporary[] = "/data/local/tmp/elf-symbol-test-XXXXXX";
    char* directory = mkdtemp(temporary);
    assert(directory);
    std::string library = std::string(directory) + "/lib.so";
    std::string symbols = std::string(directory) + "/lib.sym.so";
    auto good = fixture();
    save(library, good);
    auto resolve = [&](const std::vector<uint8_t>& bytes) {
        save(symbols, bytes);
        return get_function_rva_from_files(library.c_str(), symbols.c_str(), function_name);
    };
    assert(resolve(good) == code_address);
    assert(get_function_rva_from_files(library.c_str(), symbols.c_str(), "missing") == 0);
    assert(get_function_rva_from_files(nullptr, symbols.c_str(), function_name) == 0);
    assert(get_function_rva_from_files(library.c_str(), nullptr, function_name) == 0);
    assert(get_function_rva_from_files(library.c_str(), symbols.c_str(), nullptr) == 0);

    // File offsets differ from virtual addresses; never return the file offset.
    assert(resolve(good) != code_offset);
    auto changed = good;
    changed[note_offset + 16] ^= 1;
    assert(resolve(changed) == 0); // Other build, even if the symbol name matches.
    changed = good;
    write(changed, note_offset, Elf64_Nhdr{4, 8, 0});
    assert(resolve(changed) == 0); // Missing build ID.
    changed = good;
    write(changed, note_offset, Elf64_Nhdr{4, UINT32_MAX, NT_GNU_BUILD_ID});
    assert(resolve(changed) == 0);
    changed = good;
    auto header = read<Elf64_Ehdr>(changed, 0);
    header.e_machine = EM_X86_64;
    write(changed, 0, header);
    assert(resolve(changed) == 0);
    changed = good;
    header = read<Elf64_Ehdr>(changed, 0);
    header.e_shoff = std::numeric_limits<uint64_t>::max() - 10;
    write(changed, 0, header);
    assert(resolve(changed) == 0);
    changed = good;
    auto section = read<Elf64_Shdr>(changed, section_offset + 3 * sizeof(Elf64_Shdr));
    section.sh_link = 500;
    write(changed, section_offset + 3 * sizeof(section), section);
    assert(resolve(changed) == 0);
    changed = good;
    section = read<Elf64_Shdr>(changed, section_offset + 3 * sizeof(Elf64_Shdr));
    section.sh_entsize = 1;
    write(changed, section_offset + 3 * sizeof(section), section);
    assert(resolve(changed) == 0);
    changed = good;
    auto symbol = read<Elf64_Sym>(changed, symbol_offset + sizeof(Elf64_Sym));
    symbol.st_name = UINT32_MAX;
    write(changed, symbol_offset + sizeof(symbol), symbol);
    assert(resolve(changed) == 0);
    changed = good;
    changed[string_offset + std::strlen(function_name) + 1] = 'X';
    assert(resolve(changed) == 0); // Unterminated symbol name.
    for (auto kind : {STT_OBJECT, STT_NOTYPE}) {
        changed = good;
        symbol = read<Elf64_Sym>(changed, symbol_offset + sizeof(Elf64_Sym));
        symbol.st_info = ELF64_ST_INFO(STB_LOCAL, kind);
        write(changed, symbol_offset + sizeof(symbol), symbol);
        assert(resolve(changed) == 0);
    }
    changed = good;
    symbol = read<Elf64_Sym>(changed, symbol_offset + sizeof(Elf64_Sym));
    symbol.st_shndx = SHN_UNDEF;
    write(changed, symbol_offset + sizeof(symbol), symbol);
    assert(resolve(changed) == 0);
    changed = good;
    symbol = read<Elf64_Sym>(changed, symbol_offset + sizeof(Elf64_Sym));
    symbol.st_value = code_address + 0x40;
    write(changed, symbol_offset + sizeof(symbol), symbol);
    assert(resolve(changed) == 0); // Outside executable bytes.
    changed = good;
    symbol = read<Elf64_Sym>(changed, symbol_offset + sizeof(Elf64_Sym));
    symbol.st_value++;
    write(changed, symbol_offset + sizeof(symbol), symbol);
    assert(resolve(changed) == 0); // Unaligned ARM64 instruction.
    changed = good;
    symbol = read<Elf64_Sym>(changed, symbol_offset + sizeof(Elf64_Sym));
    write(changed, symbol_offset + 2 * sizeof(symbol), symbol);
    assert(resolve(changed) == code_address); // Duplicate identical definition.
    symbol.st_value += 0x10;
    write(changed, symbol_offset + 2 * sizeof(symbol), symbol);
    assert(resolve(changed) == 0); // Ambiguous name.

    auto non_executable = good;
    auto segment = read<Elf64_Phdr>(non_executable, sizeof(Elf64_Ehdr));
    segment.p_flags = PF_R;
    write(non_executable, sizeof(Elf64_Ehdr), segment);
    save(library, non_executable);
    assert(resolve(good) == 0);
    save(library, good);
    for (size_t size = 0; size < sizeof(Elf64_Ehdr); ++size)
        assert(resolve(std::vector<uint8_t>(good.begin(), good.begin() + size)) == 0);

    if (argc == 3) {
        constexpr const char* path_symbol =
            "_ZL21IsInForbiddenLocationRKN4core12basic_stringIcNS_20StringStorageDefaultIcEEEEPKPKcm";
        constexpr const char* invoke_symbol =
            "_Z23scripting_method_invoke18ScriptingMethodPtr18ScriptingObjectPtr"
            "R18ScriptingArgumentsP21ScriptingExceptionPtrb";
        auto path = get_function_rva_from_files(argv[1], argv[2], path_symbol);
        auto invoke = get_function_rva_from_files(argv[1], argv[2], invoke_symbol);
        assert(path && invoke && path != invoke);
        std::printf("Bundled Unity: IsInForbiddenLocation=0x%zx, scripting_method_invoke=0x%zx\n",
                    path, invoke);
    }
    unlink(symbols.c_str());
    unlink(library.c_str());
    rmdir(directory);
    std::puts("PASS: build-ID matching, unique function resolution, executable bounds, malformed/truncated ELF rejection");
}
