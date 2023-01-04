// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from example.djinni

#include "NativeTextboxListener.hpp"  // my header
#include "NativeItemList.hpp"

namespace djinni_generated {


void NativeTextboxListener::JsProxy::update(const ::textsort::ItemList & items) {
    auto ret = callMethod("update", ::djinni_generated::NativeItemList::fromCpp(items));
    checkError(ret);
}

EMSCRIPTEN_BINDINGS(textsort_textbox_listener) {
    em::class_<::textsort::TextboxListener>("TextboxListener")
        .smart_ptr<std::shared_ptr<::textsort::TextboxListener>>("TextboxListener")
        .function("nativeDestroy", &NativeTextboxListener::nativeDestroy)
        ;
}

} // namespace djinni_generated