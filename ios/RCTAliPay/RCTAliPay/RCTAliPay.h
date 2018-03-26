
//#import "RCTBridgeModule.h"
#import <React/RCTBridgeModule.h>

@interface RCTAliPay : NSObject <RCTBridgeModule>

- (void) processOrderWithPaymentResult:(NSDictionary *)resultDic;

- (BOOL) processAuthResult:(NSURL *)url;
@end

