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

#include "DataRefWasm.hpp"

namespace djinni {

struct NativeDataRef {
    using CppType = DataRef;
    using JsType = em::val;

    static CppType toCpp(const JsType& data) {
        return DataRef{std::make_unique<DataRefWasm>(data)};
    }

    static JsType fromCpp(const CppType& c) {
        return c.getOrBindPlatform<DataRefWasm>()->platformObj();
    }

    using Boxed = NativeDataRef;
};
} // namespace djinni
