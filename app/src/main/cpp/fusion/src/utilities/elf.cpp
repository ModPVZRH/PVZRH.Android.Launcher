#include "utilities/elf.h"
#include <elf.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstring>
#include <limits>
#include <utility>
#include <vector>

namespace {

#if defined(__arm__)
using Header = Elf32_Ehdr;
using Section = Elf32_Shdr;
using Segment = Elf32_Phdr;
using Symbol = Elf32_Sym;
constexpr auto elf_class = ELFCLASS32;
constexpr auto elf_machine = EM_ARM;
#else
using Header = Elf64_Ehdr;
using Section = Elf64_Shdr;
using Segment = Elf64_Phdr;
using Symbol = Elf64_Sym;
constexpr auto elf_class = ELFCLASS64;
constexpr auto elf_machine = EM_AARCH64;
#endif

class ElfFile {
public:
    explicit ElfFile(const char* path) {
        if (!path) return;
        int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) return;
        struct stat st{};
        if (fstat(fd, &st) == 0 && st.st_size >= static_cast<off_t>(sizeof(Header)) &&
            static_cast<uint64_t>(st.st_size) <= std::numeric_limits<size_t>::max()) {
            size_ = static_cast<size_t>(st.st_size);
            void* data = mmap(nullptr, size_, PROT_READ, MAP_PRIVATE, fd, 0);
            if (data != MAP_FAILED) data_ = static_cast<const uint8_t*>(data);
        }
        close(fd);
        if (!data_) return;
        read(0, header_);
        valid_ = std::memcmp(header_.e_ident, ELFMAG, SELFMAG) == 0 &&
                 header_.e_ident[EI_CLASS] == elf_class &&
                 header_.e_ident[EI_DATA] == ELFDATA2LSB &&
                 header_.e_ident[EI_VERSION] == EV_CURRENT &&
                 header_.e_version == EV_CURRENT && header_.e_type == ET_DYN &&
                 header_.e_machine == elf_machine && header_.e_ehsize == sizeof(Header) &&
                 header_.e_phnum > 0 && header_.e_phnum != PN_XNUM &&
                 header_.e_phentsize == sizeof(Segment) &&
                 contains(header_.e_phoff, uint64_t(header_.e_phnum) * sizeof(Segment)) &&
                 (header_.e_shnum == 0 ? header_.e_shoff == 0 :
                  header_.e_shentsize == sizeof(Section) &&
                  contains(header_.e_shoff, uint64_t(header_.e_shnum) * sizeof(Section)));
    }

    ~ElfFile() { if (data_) munmap(const_cast<uint8_t*>(data_), size_); }
    ElfFile(const ElfFile&) = delete;
    ElfFile& operator=(const ElfFile&) = delete;

    bool valid() const { return valid_; }
    bool contains(uint64_t offset, uint64_t length) const {
        return data_ && offset <= size_ && length <= size_ - offset;
    }

    template<class T> bool read(uint64_t offset, T& result) const {
        if (!contains(offset, sizeof(T))) return false;
        std::memcpy(&result, data_ + offset, sizeof(T));
        return true;
    }

    bool section(size_t index, Section& result) const {
        return index < header_.e_shnum &&
               read(header_.e_shoff + index * sizeof(Section), result);
    }

    bool executable(uint64_t address, uint64_t length) const {
        for (size_t i = 0; i < header_.e_phnum; ++i) {
            Segment segment{};
            if (!read(header_.e_phoff + i * sizeof(Segment), segment)) return false;
            if (segment.p_type == PT_LOAD && (segment.p_flags & (PF_R | PF_X)) == (PF_R | PF_X) &&
                contains(segment.p_offset, segment.p_filesz) && address >= segment.p_vaddr &&
                address - segment.p_vaddr <= segment.p_filesz &&
                length <= segment.p_filesz - (address - segment.p_vaddr)) return true;
        }
        return false;
    }

    std::vector<uint8_t> build_id() const {
        std::vector<uint8_t> result;
        // PT_NOTE works even without section headers. Debug-only companions
        // can preserve notes in sections while removing executable data.
        for (size_t i = 0; i < header_.e_phnum; ++i) {
            Segment segment{};
            if (!read(header_.e_phoff + i * sizeof(Segment), segment)) return {};
            if (segment.p_type == PT_NOTE &&
                !read_notes(segment.p_offset, segment.p_filesz, result)) return {};
        }
        for (size_t i = 0; i < header_.e_shnum; ++i) {
            Section notes{};
            if (!section(i, notes)) return {};
            if (notes.sh_type == SHT_NOTE && !read_notes(notes.sh_offset, notes.sh_size, result)) return {};
        }
        return result;
    }

    uintptr_t function(const char* name, const ElfFile& library) const {
        uintptr_t found = 0;
        uint64_t found_size = 0;
        size_t name_size = std::strlen(name) + 1;
        for (size_t i = 0; i < header_.e_shnum; ++i) {
            Section table{};
            if (!section(i, table)) return 0;
            if (table.sh_type != SHT_SYMTAB && table.sh_type != SHT_DYNSYM) continue;
            Section strings{};
            if (table.sh_entsize != sizeof(Symbol) || table.sh_size % sizeof(Symbol) != 0 ||
                !contains(table.sh_offset, table.sh_size) ||
                !section(table.sh_link, strings) || strings.sh_type != SHT_STRTAB ||
                !contains(strings.sh_offset, strings.sh_size)) return 0;
            for (uint64_t j = 0; j < table.sh_size / sizeof(Symbol); ++j) {
                Symbol symbol{};
                if (!read(table.sh_offset + j * sizeof(Symbol), symbol)) return 0;
                if (symbol.st_name >= strings.sh_size) return 0;
                if (name_size > strings.sh_size - symbol.st_name ||
                    std::memcmp(data_ + strings.sh_offset + symbol.st_name, name, name_size) != 0) continue;
                // The same definition may appear in both SYMTAB and DYNSYM.
                if (symbol.st_shndx == SHN_UNDEF) continue;
                Section code{};
                if (ELF64_ST_TYPE(symbol.st_info) != STT_FUNC || symbol.st_size < 4 ||
                    symbol.st_value == 0 || symbol.st_value % 4 != 0 ||
                    !section(symbol.st_shndx, code) || !(code.sh_flags & SHF_EXECINSTR) ||
                    symbol.st_value < code.sh_addr || symbol.st_value - code.sh_addr > code.sh_size ||
                    symbol.st_size > code.sh_size - (symbol.st_value - code.sh_addr) ||
                    !library.executable(symbol.st_value, symbol.st_size) ||
                    (found && (found != symbol.st_value || found_size != symbol.st_size))) return 0;
                found = static_cast<uintptr_t>(symbol.st_value);
                found_size = symbol.st_size;
            }
        }
        return found;
    }

private:
    bool read_notes(uint64_t offset, uint64_t length, std::vector<uint8_t>& result) const {
        if (!contains(offset, length)) return false;
        uint64_t cursor = offset;
        const uint64_t end = offset + length;
        while (cursor < end) {
            Elf32_Nhdr note{};
            if (end - cursor < sizeof(note) || !read(cursor, note)) return false;
            cursor += sizeof(note);
            uint64_t names = (uint64_t(note.n_namesz) + 3) & ~uint64_t(3);
            uint64_t desc = (uint64_t(note.n_descsz) + 3) & ~uint64_t(3);
            if (names > end - cursor || desc > end - cursor - names) return false;
            if (note.n_type == NT_GNU_BUILD_ID && note.n_namesz == 4 &&
                std::memcmp(data_ + cursor, "GNU\0", 4) == 0) {
                if (note.n_descsz == 0 || note.n_descsz > 64) return false;
                std::vector<uint8_t> id(data_ + cursor + names, data_ + cursor + names + note.n_descsz);
                if (!result.empty() && result != id) return false;
                result = std::move(id);
            }
            cursor += names + desc;
        }
        return true;
    }

    const uint8_t* data_ = nullptr;
    size_t size_ = 0;
    Header header_{};
    bool valid_ = false;
};

} // namespace

uintptr_t get_function_rva_from_files(const char* library_path, const char* symbol_path,
                                      const char* target_symbol) {
    if (!target_symbol || !*target_symbol) return 0;
    ElfFile library(library_path), symbols(symbol_path);
    if (!library.valid() || !symbols.valid()) return 0;
    auto build_id = library.build_id();
    if (build_id.empty() || build_id != symbols.build_id()) return 0;
    return symbols.function(target_symbol, library);
}
