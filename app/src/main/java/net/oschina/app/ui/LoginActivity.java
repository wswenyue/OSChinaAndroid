package net.oschina.app.ui;

import net.oschina.app.AppConfig;
import net.oschina.app.AppContext;
import net.oschina.app.R;
import net.oschina.app.api.ApiHttpClient;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.base.BaseActivity;
import net.oschina.app.bean.Constants;
import net.oschina.app.bean.LoginUserBean;
import net.oschina.app.bean.OpenIdCatalog;
import net.oschina.app.util.CyptoUtils;
import net.oschina.app.util.DialogHelp;
import net.oschina.app.util.TDevice;
import net.oschina.app.util.TLog;
import net.oschina.app.util.XmlUtils;

import org.apache.http.Header;
import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.protocol.HttpContext;
import org.kymjs.kjframe.http.HttpConfig;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import butterknife.InjectView;
import butterknife.OnClick;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.tencent.mm.sdk.modelbase.BaseReq;
import com.tencent.mm.sdk.modelbase.BaseResp;
import com.tencent.mm.sdk.modelmsg.SendAuth;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.sdk.openapi.WXAPIFactory;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;
import com.umeng.socialize.bean.SHARE_MEDIA;
import com.umeng.socialize.controller.UMServiceFactory;
import com.umeng.socialize.controller.UMSocialService;
import com.umeng.socialize.controller.listener.SocializeListeners;
import com.umeng.socialize.exception.SocializeException;
import com.umeng.socialize.sso.SinaSsoHandler;

import java.util.Map;
import java.util.Set;

/**
 * 用户登录界面
 *
 * @author kymjs (http://www.kymjs.com/)
 */
public class LoginActivity extends BaseActivity implements IWXAPIEventHandler, IUiListener {

    public static final int REQUEST_CODE_INIT = 0;
    private static final String BUNDLE_KEY_REQUEST_CODE = "BUNDLE_KEY_REQUEST_CODE";
    protected static final String TAG = LoginActivity.class.getSimpleName();

    @InjectView(R.id.et_username)
    EditText mEtUserName;

    @InjectView(R.id.et_password)
    EditText mEtPassword;

    private final int requestCode = REQUEST_CODE_INIT;
    private String mUserName = "";
    private String mPassword = "";

    private Tencent mTencent;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_login;
    }

    @Override
    public void initView() {

    }

    @Override
    protected boolean hasBackButton() {
        return true;
    }

    @Override
    protected int getActionBarTitle() {
        return R.string.login;
    }

    @Override
    @OnClick({R.id.btn_login, R.id.iv_qq_login, R.id.iv_wx_login, R.id.iv_sina_login})
    public void onClick(View v) {

        int id = v.getId();
        switch (id) {
            case R.id.btn_login:
                handleLogin();
                break;
            case R.id.iv_qq_login:
                qqLogin();
                break;
            case R.id.iv_wx_login:
                wxLogin();
                break;
            case R.id.iv_sina_login:
                sinaLogin();
                break;
            default:
                break;
        }
    }

    private void handleLogin() {

        if (prepareForLogin()) {
            return;
        }

        // if the data has ready
        mUserName = mEtUserName.getText().toString();
        mPassword = mEtPassword.getText().toString();

        showWaitDialog(R.string.progress_login);
        OSChinaApi.login(mUserName, mPassword, mHandler);
    }

    private final AsyncHttpResponseHandler mHandler = new AsyncHttpResponseHandler() {

        @Override
        public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
            LoginUserBean loginUserBean = XmlUtils.toBean(LoginUserBean.class, arg2);
            if (loginUserBean != null) {
                handleLoginBean(loginUserBean);
            }
        }

        @Override
        public void onFailure(int arg0, Header[] arg1, byte[] arg2,
                              Throwable arg3) {
            AppContext.showToast(R.string.tip_login_error_for_network);
        }

        @Override
        public void onFinish() {
            super.onFinish();
            hideWaitDialog();
        }
    };

    private void handleLoginSuccess() {
        Intent data = new Intent();
        data.putExtra(BUNDLE_KEY_REQUEST_CODE, requestCode);
        setResult(RESULT_OK, data);
        this.sendBroadcast(new Intent(Constants.INTENT_ACTION_USER_CHANGE));
        finish();
    }

    private boolean prepareForLogin() {
        if (!TDevice.hasInternet()) {
            AppContext.showToastShort(R.string.tip_no_internet);
            return true;
        }
        if (mEtUserName.length() == 0) {
            mEtUserName.setError("请输入邮箱/用户名");
            mEtUserName.requestFocus();
            return true;
        }

        if (mEtPassword.length() == 0) {
            mEtPassword.setError("请输入密码");
            mEtPassword.requestFocus();
            return true;
        }

        return false;
    }

    @Override
    public void initData() {
        mTencent = Tencent.createInstance(AppConfig.APP_QQ_KEY,
                this.getApplicationContext());

        mEtUserName.setText(AppContext.getInstance()
                .getProperty("user.account"));
        mEtPassword.setText(CyptoUtils.decode("oschinaApp", AppContext
                .getInstance().getProperty("user.pwd")));
    }

    /**
     * QQ登陆
     */
    private void qqLogin() {
        if (!mTencent.isSessionValid()) {
            mTencent.login(this, "all", this);
        }
    }

    /**
     * 微信登陆
     */
    private void wxLogin() {
        IWXAPI api = WXAPIFactory.createWXAPI(this, Constants.WEICHAT_APPID, false);
        api.registerApp(Constants.WEICHAT_APPID);
        api.handleIntent(getIntent(), this);
        final SendAuth.Req req = new SendAuth.Req();
        req.scope = "snsapi_userinfo";
        req.state = "none";
        api.sendReq(req);
    }


    /**
     * 新浪登录
     */
    private void sinaLogin() {
        final UMSocialService mController = UMServiceFactory.getUMSocialService("com.umeng.login");
        SinaSsoHandler sinaSsoHandler = new SinaSsoHandler();
        mController.getConfig().setSsoHandler(sinaSsoHandler);
        mController.doOauthVerify(this, SHARE_MEDIA.SINA,
                new SocializeListeners.UMAuthListener() {

                    @Override
                    public void onStart(SHARE_MEDIA arg0) {
                    }

                    @Override
                    public void onError(SocializeException arg0,
                                        SHARE_MEDIA arg1) {
                        AppContext.showToast("新浪授权失败");
                    }

                    @Override
                    public void onComplete(Bundle value, SHARE_MEDIA arg1) {
                        if (value != null && !TextUtils.isEmpty(value.getString("uid"))) {
                            // 获取平台信息
                            mController.getPlatformInfo(LoginActivity.this, SHARE_MEDIA.SINA, new SocializeListeners.UMDataListener() {
                                @Override
                                public void onStart() {

                                }

                                @Override
                                public void onComplete(int i, Map<String, Object> map) {
                                    if (i == 200 && map != null) {
                                        StringBuilder sb = new StringBuilder("{");
                                        Set<String> keys = map.keySet();
                                        int index = 0;
                                        for (String key : keys) {
                                            index++;
                                            String jsonKey = key;
                                            if (jsonKey.equals("uid")) {
                                                jsonKey = "openid";
                                            }
                                            sb.append(String.format("\"%s\":\"%s\"", jsonKey, map.get(key).toString()));
                                            if (index != map.size()) {
                                                sb.append(",");
                                            }
                                        }
                                        sb.append("}");
                                        openIdLogin(OpenIdCatalog.WEIBO, sb.toString());
                                    } else {
                                        AppContext.showToast("发生错误：" + i);
                                    }
                                }
                            });
                        } else {
                            AppContext.showToast("授权失败");
                        }
                    }

                    @Override
                    public void onCancel(SHARE_MEDIA arg0) {
                        AppContext.showToast("已取消新浪登陆");
                    }
                });
    }

    @Override
    public void onReq(BaseReq baseReq) {

    }

    @Override
    public void onResp(BaseResp baseResp) {

        switch (baseResp.errCode) {
            case BaseResp.ErrCode.ERR_OK:
                String code = ((SendAuth.Resp) baseResp).code;
                //上面的code就是接入指南里要拿到的code
                DialogHelp.getMessageDialog(this, code + "sdff").show();
                break;

            default:
                break;
        }
    }

    // 获取到QQ授权登陆的信息
    @Override
    public void onComplete(Object o) {
        openIdLogin(OpenIdCatalog.QQ, o.toString());
    }

    @Override
    public void onError(UiError uiError) {

    }

    @Override
    public void onCancel() {

    }

    /***
     *
     * @param catalog 第三方登录的类别
     * @param openIdInfo 第三方的信息
     */
    private void openIdLogin(final String catalog, final String openIdInfo) {
        final ProgressDialog waitDialog = DialogHelp.getWaitDialog(this, "登陆中...");
        OSChinaApi.open_login(catalog, openIdInfo, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                LoginUserBean loginUserBean = XmlUtils.toBean(LoginUserBean.class, responseBody);
                if (loginUserBean.getResult().OK()) {
                    handleLoginBean(loginUserBean);
                } else {
                    // 前往绑定或者注册操作
                    Intent intent = new Intent(LoginActivity.this, LoginBindActivityChooseActivity.class);
                    intent.putExtra(LoginBindActivityChooseActivity.BUNDLE_KEY_CATALOG, catalog);
                    intent.putExtra(LoginBindActivityChooseActivity.BUNDLE_KEY_OPENIDINFO, openIdInfo);
                    startActivityForResult(intent, REQUEST_CODE_OPENID);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {

            }

            @Override
            public void onStart() {
                super.onStart();
                waitDialog.show();
            }

            @Override
            public void onFinish() {
                super.onFinish();
                waitDialog.dismiss();
            }
        });
    }

    public static final int REQUEST_CODE_OPENID = 1000;
    // 登陆实体类
    public static final String BUNDLE_KEY_LOGINBEAN = "bundle_key_loginbean";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_OPENID:
                if (data == null) {
                    return;
                }
                LoginUserBean loginUserBean = (LoginUserBean) data.getSerializableExtra(BUNDLE_KEY_LOGINBEAN);
                if (loginUserBean !=  null) {
                    AppContext.showToast(loginUserBean.getUser().toString());
                    handleLoginBean(loginUserBean);
                }
                break;
            default:

                break;
        }
    }

    // 处理loginBean
    private void handleLoginBean(LoginUserBean loginUserBean) {
        if (loginUserBean.getResult().OK()) {
            AsyncHttpClient client = ApiHttpClient.getHttpClient();
            HttpContext httpContext = client.getHttpContext();
            CookieStore cookies = (CookieStore) httpContext
                    .getAttribute(ClientContext.COOKIE_STORE);
            if (cookies != null) {
                String tmpcookies = "";
                for (Cookie c : cookies.getCookies()) {
                    TLog.log(TAG,
                            "cookie:" + c.getName() + " " + c.getValue());
                    tmpcookies += (c.getName() + "=" + c.getValue()) + ";";
                }
                TLog.log(TAG, "cookies:" + tmpcookies);
                AppContext.getInstance().setProperty(AppConfig.CONF_COOKIE,
                        tmpcookies);
                ApiHttpClient.setCookie(ApiHttpClient.getCookie(AppContext
                        .getInstance()));
                HttpConfig.sCookie = tmpcookies;
            }
            // 保存登录信息
            loginUserBean.getUser().setAccount(mUserName);
            loginUserBean.getUser().setPwd(mPassword);
            loginUserBean.getUser().setRememberMe(true);
            AppContext.getInstance().saveUserInfo(loginUserBean.getUser());
            hideWaitDialog();
            handleLoginSuccess();

        } else {
            AppContext.getInstance().cleanLoginInfo();
            AppContext.showToast(loginUserBean.getResult().getErrorMessage());
        }
    }
}
