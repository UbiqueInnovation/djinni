
#pragma once

#include "../cpp/DataRef.hpp"
#include <memory>
#include <vector>

namespace djinni {
class DataRefJNI : public DataRef::PlatformRef {
public:
    using PlatformObject = void*;

    DataRefJNI() = delete;
    DataRefJNI(const DataRefJNI&) = delete;
    DataRefJNI(DataRefJNI&&);
    virtual ~DataRefJNI();
    
    // initialize with vector and try not to copy it
    explicit DataRefJNI(std::vector<uint8_t>&& vec);

    explicit DataRefJNI(void* platformObj);

    virtual const uint8_t* buf() const override;
    virtual size_t len() const override; 
    virtual uint8_t* mutableBuf() override; 
    
    PlatformObject platformObj() const;

private:
    class Internal;
    std::unique_ptr<Internal> _impl;
};
} // namespace djinni
