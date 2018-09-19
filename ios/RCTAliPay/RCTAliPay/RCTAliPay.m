
#import <AlipaySDK/AlipaySDK.h>
#import "RCTAliPay.h"
#import "Ali/Util/base64.h"
#import "Ali/Util/RSADataSigner.h"
#import "Ali/APAuthV2Info.h"
#import <React/RCTEventDispatcher.h>
#import <React/RCTBridge.h>
#import "NSString+S4S_NSString.h"

@implementation RCTAliPay

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE(AliPay);

- (id)init
{
    if (self = [super init]) {
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(processOrderWithPaymentResult:) name:@"RCTOpenURLNotification" object:nil];
    }
    return self;
}

RCT_EXPORT_METHOD(payOrder:(NSDictionary *)params){
    
    NSString *orderText = [params objectForKey:@"orderText"];
    NSString *appScheme = [params objectForKey:@"appScheme"];   //应用注册scheme, 对应需要在Info.plist定义URL types
    
    // NOTE: 调用支付结果开始支付, 如没有安装支付宝app，则会走h5页面，支付回调触发这里的callback
    dispatch_async(dispatch_get_main_queue(), ^{
        [[AlipaySDK defaultService] payOrder:orderText fromScheme:appScheme callback:^(NSDictionary *resultDic) {
        }];
    });
}

- (void)processOrderWithPaymentResult:(NSNotification *)aNotification {
    
    NSString * aURLString =  [aNotification userInfo][@"url"];
    NSURL * url = [NSURL URLWithString:aURLString];
    if ([url.host isEqualToString:@"safepay"]) {
        NSString *urlString = [NSString stringWithFormat:@"%@", url];
        [self.bridge.eventDispatcher sendAppEventWithName:@"alipay.mobile.securitypay.pay.onPaymentResult"
                                                     body:urlString];
    }
}

- (BOOL)processAuthResult:(NSNotification *)aNotification {
    NSString * aURLString =  [aNotification userInfo][@"url"];
    NSURL * url = [NSURL URLWithString:aURLString];
    NSLog(@"RCTAliPay -> processAuthResult url = %@", url);
    if ([url.host isEqualToString:@"safepay"]) {
        NSString *urlString = [NSString stringWithFormat:@"%@", url];
        NSDictionary *dict = [urlString getURLParameters];
        NSLog(@"RCTAliPay -> processAuthResult resultDic = %@ %@", urlString, dict);
        [self.bridge.eventDispatcher sendAppEventWithName:@"alipay.mobile.auth.onAuthResult"
                                                     body:dict];
        return YES;
    }
    return NO;
}

RCT_EXPORT_METHOD(login:(NSDictionary *) params) {
    NSString *appid = [params objectForKey:@"appid"];
    NSString *pid = [params objectForKey:@"pid"];
    NSString *targetid = [params objectForKey:@"target_id"];
    NSString *rsa2_private = [params objectForKey:@"rsa2_private"];
    NSString *rsa_private = [params objectForKey:@"rsa_private"];
    if (appid == nil || pid == nil || targetid == nil || rsa_private == nil) {
        printf("no auth info || appid || pid || private key");
    }
    
    APAuthV2Info *authInfo = [[APAuthV2Info alloc] init];
    authInfo.pid = pid;
    authInfo.appID = appid;
    authInfo.authType = @"LOGIN";
    
    NSString *authInfoStr = authInfo.description;
    
    RSADataSigner *signer = [[RSADataSigner alloc]initWithPrivateKey:rsa_private];
    
    NSString *signStr = [signer signString:authInfoStr withRSA2:false ];
    
    //    NSString totalAuthInfoStr = "\(authInfoStr)&sign=\(signStr ?? "")&sign_type=RSA";
    NSString *totalAuthInfoStr = [NSString stringWithFormat:@"\(%@)&sign=\(%@)&sign_type=RSA", authInfoStr, signStr];
    
    [[AlipaySDK defaultService] auth_V2WithInfo:totalAuthInfoStr
                                     fromScheme:@"S4SFinancialClient"
                                       callback:^(NSDictionary *resultDic) {
                                           NSLog(@"login reslut = %@",resultDic);
                                       }];
    
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end

