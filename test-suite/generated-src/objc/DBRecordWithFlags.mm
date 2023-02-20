// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from enum_flags.djinni

#import "DBRecordWithFlags.h"


@implementation DBRecordWithFlags

- (nonnull instancetype)initWithAccess:(DBAccessFlags)access
{
    if (self = [super init]) {
        _access = access;
    }
    return self;
}

+ (nonnull instancetype)recordWithFlagsWithAccess:(DBAccessFlags)access
{
    return [[self alloc] initWithAccess:access];
}

#ifndef DJINNI_DISABLE_DESCRIPTION_METHODS
- (NSString *)description
{
    return [NSString stringWithFormat:@"<%@ %p access:%@>", self.class, (void *)self, @(self.access)];
}

#endif
@end
