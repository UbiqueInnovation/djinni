// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from proto.djinni

#import "proto/cpp/test2.pb.h"
#import <Foundation/Foundation.h>

@interface DBRecordWithEmbeddedCppProto : NSObject
- (nonnull instancetype)init NS_UNAVAILABLE;
+ (nonnull instancetype)new NS_UNAVAILABLE;
- (nonnull instancetype)initWithState:(const djinni::test2::PersistingState & )state NS_DESIGNATED_INITIALIZER;
+ (nonnull instancetype)RecordWithEmbeddedCppProtoWithState:(const djinni::test2::PersistingState & )state;

@property (nonatomic, readonly) djinni::test2::PersistingState state;

@end
