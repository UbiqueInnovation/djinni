/**
  * Copyright 2021 Snap, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

#pragma once
#ifdef __cplusplus

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <vector>

namespace djinni {

class DataRef {
public:
    // PlatformRef is a platform-specific data reference.
    // For DataRefs created from C++, this is lazily initialized when the
    // object is translated to the platform.
    class PlatformRef {
    public:
        virtual ~PlatformRef() = default;
        
        virtual const uint8_t* buf() const = 0;
        virtual size_t len() const = 0;
        virtual uint8_t* mutableBuf() = 0;
    };

    DataRef(const DataRef&) = default;
    DataRef(DataRef&&) = default;

    explicit DataRef(size_t len) : _ref(std::make_shared<RefState>(len)) {}
    explicit DataRef(const std::vector<uint8_t> &vec) : _ref(std::make_shared<RefState>(vec.data(), vec.size())) {}
    explicit DataRef(std::vector<uint8_t> &&vec) : _ref(std::make_shared<RefState>(std::move(vec))) {}
    explicit DataRef(const void *data, size_t len) : _ref(std::make_shared<RefState>(data, len)) {}
    explicit DataRef(const std::string &str) : _ref(std::make_shared<RefState>(str.data(), str.size())) {}
    explicit DataRef(std::string &&str) : _ref(std::make_shared<RefState>(std::move(str))) {}
    explicit DataRef(std::unique_ptr<PlatformRef> platform) : _ref(std::make_shared<RefState>(std::move(platform))) {}

    DataRef& operator=(const DataRef&) = default;
    DataRef& operator=(DataRef&&) = default;
    
    const uint8_t* buf() const {
        return _ref ? _ref->buf() : nullptr;
    }
    size_t len() const {
        return _ref ? _ref->len() : 0;
    }
    uint8_t* mutableBuf() {
        return _ref ? _ref->mutableBuf() : nullptr;
    }

    template<typename PlatformRefT>
    const PlatformRefT* getOrBindPlatform() const {
        // Internal mutate
        return _ref ? _ref->getOrBindPlatform<PlatformRefT>() : nullptr;
    }

private:
    // RefState contains either the native C++ data, or the platform specific reference object.
    // If the DataRef was created from C++, this will be bound to the platform only when explicitly accessed from there.
    class RefState {
    private:
        mutable std::mutex _mutex;
        std::optional<std::vector<uint8_t>> _cpp;
        std::unique_ptr<PlatformRef> _platform;
    public:
        explicit RefState(size_t len)
            : _cpp(len) {}
        explicit RefState(std::vector<uint8_t> &&vec)
            : _cpp(std::move(vec)) {}
        explicit RefState(const void *data, size_t len)
            : _cpp(len) {
            memcpy(_cpp->data(), data, len);
        }
        explicit RefState(std::string &&str) : RefState(str.data(), str.size()) {}
        explicit RefState(std::unique_ptr<PlatformRef> platform) : _platform(std::move(platform)) {}

        const uint8_t* buf() const {
            std::lock_guard lock(_mutex);
            if(_cpp) {
                return _cpp->data();
            } else {
                return _platform ? _platform->buf() : nullptr;
            }
        }
        size_t len() const {
            std::lock_guard lock(_mutex);
            if(_cpp) {
                return _cpp->size();
            } else {
                return _platform ? _platform->len() : 0;
            }
        }
        uint8_t* mutableBuf() {
            std::lock_guard lock(_mutex);
            if(_cpp) {
                return _cpp->data();
            } else {
                return _platform ? _platform->mutableBuf() : nullptr;
            }
        }

        template<typename PlatformRefT>
        const PlatformRefT* getOrBindPlatform() {
            std::lock_guard lock(_mutex);
            if(_cpp) {
                _platform = std::make_unique<PlatformRefT>(std::move(_cpp.value()));
                _cpp.reset();
            }
            return dynamic_cast<PlatformRefT*>(_platform.get());
        }
    };

    std::shared_ptr<RefState> _ref;
};

} // namespace djinni
#endif
