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

#import <Foundation/Foundation.h>

NS_SWIFT_SENDABLE
@interface DJOutcome<Result, Error> : NSObject
-(instancetype _Nonnull)init NS_UNAVAILABLE;
-(id _Nullable)matchResult:(id _Nullable (^ _Nonnull)(Result _Nonnull)) resultHandler
                     Error:(id _Nullable (^ _Nonnull)(Error _Nonnull)) errorHandler;
-(Result _Nullable)result;
-(Result _Nonnull)resultOr:(Result _Nullable)defaultResult;
-(Error _Nullable)error;
-(BOOL)isEqualToOutcome:(DJOutcome* _Nullable)other;
+(DJOutcome<Result,Error>* _Nonnull)fromResult:(Result _Nonnull)result;
+(DJOutcome<Result,Error>* _Nonnull)fromError:(Error _Nonnull)error;
@end
