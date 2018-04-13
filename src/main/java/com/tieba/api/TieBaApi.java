package com.tieba.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.alibaba.fastjson.JSON;
import com.tieba.model.ClientType;
import com.tieba.model.MyTB;
import com.tieba.model.ReplyInfo;
import com.tieba.util.Constants;
import com.tieba.util.DateKit;
import com.tieba.util.HttpKit;
import com.tieba.util.JsonKit;
import com.tieba.util.MD5Kit;
import com.tieba.util.StrKit;

/**
 * 贴吧api
 * @author libs
 * 2018-4-8
 */
public class TieBaApi {
	
	private Logger logger = LogManager.getLogger(getClass());
	
	public static final TieBaApi api = new TieBaApi();
	
	private HttpKit hk = HttpKit.getInstance();
	
	/**
	 * 百度登录（获取bduss、stoken）
	 * @param userName
	 * @param password
	 * @param verifyCode
	 * @param codeString
	 * @param cookie
	 * @param token
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws Exception
	 */
	public Map<String, Object> getBaiDuLoginCookie(String userName, String password, String verifyCode,
			String codeString, String cookie, String token){
		Map<String, Object> map = new HashMap<String, Object>();
		try {
			if(StrKit.isBlank(token)){
				//1.访问百度首页，获取cookie baiduid
				hk.execute(Constants.BAIDU_URL);
				//2.保持会话获取token
				token = this.token();
			}
			//1.访问百度首页，获取cookie baiduid
			//bc.execute(BAIDU_URL,null,null);
			//2.保持会话获取token
			//token = this.token();
			logger.debug("正在登录。");
			//3.提交百度登录
			HttpResponse response = hk.execute(Constants.LOGIN_POST_URL, cookie, genercFormEntity(userName,password,token,verifyCode,codeString), null);
			String result = EntityUtils.toString(response.getEntity());
			String statusCode = StrKit.substring(result, "err_no=", "&");
			switch(statusCode) {  
			    case "0":
			    	//登录成功
			    	map.put("status", 0);
					map.put("message", "登录成功");
					//获取百度cookie（bduss、stoken）
					map.put("bduss", "");
					map.put("stoken", "");
			    break;  
			    case "18":
			    	//探测到您的帐号存在安全风险，建议关联手机号提高安全性(未绑定手机)
			    	map.put("status", 0);
			    	map.put("message", "登录成功");
			    	//获取百度cookie（bduss、stoken）
			    	Header[] headerArr = response.getHeaders("Set-Cookie");
			    	String bduss = "";
			    	String stoken = "";
			    	String ptoken = "";
					for (Header header : headerArr) {
						String cookieHeader = header.getValue();
						if(cookieHeader.contains("BDUSS=")){
							bduss = StrKit.substring(cookieHeader, "BDUSS=", ";");
							map.put("bduss", bduss);
						}else if(cookieHeader.contains("PTOKEN=")){
							ptoken = StrKit.substring(cookieHeader, "PTOKEN=", ";");
							map.put("ptoken", ptoken);
						}
					}
					stoken = hk.doGetStoken(Constants.PASSPORT_AUTH_URL,createCookie(bduss, null, ptoken));
					logger.debug("bduss:\t"+bduss);
					logger.debug("ptoken:\t"+ptoken);
					logger.debug("stoken:\t"+stoken);
					map.put("stoken", stoken);
			    	break;  
			    case "400031":
			    	//账号开启了登录保护
			    	map.put("status", -2);
					map.put("message", "账号开启了登录保护，请关闭");
			    	break;  
			    case "4":
			    	//用户名或密码错误
			    	map.put("status", -3);
					map.put("message", "用户名或密码错误");
					break;  
			    case "257":
			    	//请输入验证码
			    	String codestring = StrKit.substring(result, "&codeString=", "&userName");
			    	map.put("status", -1);
					map.put("message", "请输入验证码");
					map.put("imgUrl", Constants.CAPTCHA_IMG+"?"+codestring);
					map.put("cookies", hk.getCookies());
					map.put("codestring", codestring);
					map.put("token", token);
					break;
			    case "6":
			    	//验证码错误
			    	String codestring1 = StrKit.substring(result, "&codeString=", "&userName");
			    	map.put("status", -1);
			    	map.put("message", "请输入验证码");
			    	map.put("imgUrl", Constants.CAPTCHA_IMG+"?"+codestring1);
			    	map.put("cookies", hk.getCookies());
			    	map.put("codestring", codestring1);
			    	map.put("token", token);
			    	break;
			    default:
			    	//其他未知错误
			    	map.put("status", -4);
					map.put("message", "其他错误");
			    break;  
			}  
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return map;
	}
	
	/**
	 * 登录POST参数
	 * @param userName
	 * @param password
	 * @param token
	 * @param verifyCode
	 * @param codeString
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private List<NameValuePair> genercFormEntity(String userName, String password, String token, String verifyCode, String codeString) throws UnsupportedEncodingException{
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("apiver", "v3"));
		list.add(new BasicNameValuePair("charset", "UTF-8"));
		list.add(new BasicNameValuePair("codestring", codeString));
		list.add(new BasicNameValuePair("isPhone", "false"));
		list.add(new BasicNameValuePair("logintype", "bascilogin"));
		list.add(new BasicNameValuePair("password", password));
		list.add(new BasicNameValuePair("ppui_logintime", "8888"));
		list.add(new BasicNameValuePair("quick_user", "0"));
		list.add(new BasicNameValuePair("safeflg", "0"));
		list.add(new BasicNameValuePair("splogin", "rate"));
		list.add(new BasicNameValuePair("staticpage", "http://tieba.baidu.com/tb/static-common/html/pass/v3Jump.html"));
		list.add(new BasicNameValuePair("token", token));
		list.add(new BasicNameValuePair("tpl", "tb"));
		list.add(new BasicNameValuePair("tt", String.valueOf((System.currentTimeMillis() / 1000)) + "520"));
		list.add(new BasicNameValuePair("u", "http://tieba.baidu.com/"));
		list.add(new BasicNameValuePair("username", userName));
		list.add(new BasicNameValuePair("verifycode", verifyCode));
		return list;
	}
	
	/**
	 * 获取登陆token
	 * @return
	 * @throws Exception
	 */
	public String token() throws Exception{
		String token = null;
		HttpResponse response = hk.execute(Constants.TOKEN_GET_URL);
		String str = EntityUtils.toString(response.getEntity());
		Pattern pattern = Pattern.compile("token\" : \"(.*?)\"");
		Matcher matcher = pattern.matcher(str);
		if(matcher.find()){
			token = matcher.group(1);
		}
		return token;
	}
	
	/**
	 * 一键签到所有贴吧
	 * @param bduss
	 * @param stoken
	 * @return
	 */
	public Map<String, Object> oneBtnToSign(String bduss, String stoken){
		Long start = System.currentTimeMillis();
		Map<String, Object> msg = new HashMap<String, Object>();
		//1.先获取用户关注的贴吧
		List<MyTB> list = getMyLikedTB(bduss, stoken);
		int totalCount = list.size();
		//2.一键签到
		List<Map<String, Object>> results = list.stream()
			.parallel()
			.map(tb -> {
				return this.signOneTieBa(tb.getTbName(), tb.getFid(), bduss);
		}).collect(Collectors.toList());
		long signCount = results.stream().filter(r -> r.get("error_code").toString().equals("0")).count();
		long signedCount = results.stream().filter(r -> r.get("error_code").toString().equals("160002")).count();
		msg.put("用户贴吧数", totalCount);
		msg.put("签到成功", signCount);
		msg.put("已签到", signedCount);
		msg.put("签到失败", (totalCount - signedCount - signCount));
		msg.put("耗时", (System.currentTimeMillis()-start)+"ms");
		return msg;
	}
	
	/**
	 * 执行签到
	 * @param tbName 想要签到的贴吧
	 * @param fid 贴吧fid
	 * @param bduss
	 */
	@SuppressWarnings({ "resource", "unchecked" })
	public Map<String, Object> signOneTieBa(String tbName, int fid, String bduss){
		Map<String, Object> tb = new HashMap<String, Object>();
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			//list.add(new BasicNameValuePair("BDUSS", substring(cookie, "BDUSS=", ";")));
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "03-00-DA-59-05-00-72-96-06-00-01-00-04-00-4C-43-01-00-34-F4-02-00-BC-25-09-00-4E-36"));
			list.add(new BasicNameValuePair("_client_type", "4"));
			list.add(new BasicNameValuePair("_client_version", "1.2.1.17"));
			list.add(new BasicNameValuePair("_phone_imei", "540b43b59d21b7a4824e1fd31b08e9a6"));
			list.add(new BasicNameValuePair("fid",  new Formatter().format("%d", fid).toString()));
			list.add(new BasicNameValuePair("kw", tbName));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			String signStr = "";
			for (NameValuePair nameValuePair : list) {
				signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
			}
			signStr += "tiebaclient!!!";
			list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
			
			HttpResponse response = hk.execute(Constants.SIGN_POST_URL, createCookie(bduss), list);
	        String result = EntityUtils.toString(response.getEntity());
	        String code = (String) JsonKit.getInfo("error_code", result);
	        String msg = (String) JsonKit.getInfo("error_msg", result);
	        Map<String, String> map = (Map<String, String>) JsonKit.getInfo("user_info", result);
	        if("0".equals(code)){//签到成功
	        	String signPoint = map == null ? "0" : map.get("sign_bonus_point");
	            if(signPoint.equals("0")){
	            	//百度抽风，签到失败，重签
	            	this.signOneTieBa(tbName, fid, bduss);
	            }
	            tb.put("exp", signPoint);
	            tb.put("countSignNum", map==null?0:Integer.parseInt(map.get("cont_sign_num")));
	            tb.put("signTime", DateKit.realTime("Asia/Shanghai"));
	            tb.put("error_msg", "签到成功");
	            //tb.set("signTime", Integer.parseInt(map.get("sign_time"))*1000);
	        }else if("160002".equals(code)){
	        	logger.debug("亲，你之前已经签过了");
	        }else if("340006".equals(code)){
	            logger.debug("贴吧本身原因导致的签到失败，如贴吧被封");
	        }else if("1990055".equals(code)){
	        	logger.debug("帐号未实名，功能禁用。请先完成帐号的手机实名验证");
	        }
	        if(StrKit.notBlank(msg)){
	        	 tb.put("error_msg", new String(msg));
	        }
	        tb.put("error_code", code);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
        return tb;
	}
	
	/**
	 * 获取tbs
	 * @throws Exception 
	 */
	public String getTbs(String bduss) throws Exception{
		HttpResponse response = hk.execute(Constants.TBS_URL, this.createCookie(bduss));
		return (String) JsonKit.getInfo("tbs", EntityUtils.toString(response.getEntity()));
	}
	
	/**
	 * 获取用户隐藏贴吧
	 * @param username 用户名
	 * @param curpn 页码
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getHideTbs(String username, Integer curpn) {
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		List<Map<String, Object>> tiebas = new ArrayList<Map<String, Object>>();
		try {
			list.add(new BasicNameValuePair("search_key", username));
			list.add(new BasicNameValuePair("_client_version", "7.0.0.0"));
			String signStr = "";
			for (NameValuePair nameValuePair : list) {
				signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
			}
			signStr += "tiebaclient!!!";
			list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
			HttpResponse response = hk.execute(Constants.SEARCH_FRIEND, null, list);
			String result = EntityUtils.toString(response.getEntity());
			if(!JsonKit.getInfo("errorno", result).toString().equals("0")) {
				logger.info("用户信息查找失败");
			}else {
				String userId = ((List<Map<String, Object>>) JsonKit.getInfo("user_info", result)).get(0).get("user_id").toString();
				this.getTbsByUid(userId, tiebas, 1, curpn);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return tiebas;
		}
		return tiebas;
	}
	
	/**
	 * 获取用户隐藏贴吧
	 * @param username 用户名
	 * @return
	 */
	public List<Map<String, Object>> getHideTbs(String username) {
		return this.getHideTbs(username, null);
	}
	
	/**
	 * 递归获取关注贴吧数
	 * @throws Exception 
	 * @throws IOException 
	 * @throws ParseException 
	 */
	@SuppressWarnings("unchecked")
	private void getTbsByUid(String uid, List<Map<String, Object>> tiebas, int page, Integer curpn) throws ParseException, IOException, Exception {
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("_client_version", "7.0.0.0"));
		list.add(new BasicNameValuePair("friend_uid", uid));
		list.add(new BasicNameValuePair("is_guest", "1"));
		list.add(new BasicNameValuePair("page_no", (curpn == null?page:curpn) + ""));
		list.add(new BasicNameValuePair("uid", "666"));
		String signStr = "";
		for (NameValuePair nameValuePair : list) {
			signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
		}
		signStr += "tiebaclient!!!";
		list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
		String tStr =  EntityUtils.toString(hk.execute(Constants.GET_USER_TIEBA, null, list).getEntity());
		String  hasMore =  JsonKit.getInfo("has_more", tStr).toString();
		Map<String, Object> j1;
		j1 = (Map<String, Object>) JsonKit.getInfo("forum_list", tStr);
		List<Map<String, Object>> lj3 = (List<Map<String, Object>>) j1.get("non-gconforum");
		if(lj3 != null) {
			tiebas.addAll(lj3);
		}
		List<Map<String, Object>> lj4 = (List<Map<String, Object>>) j1.get("gconforum");
		if(lj4 != null) {
			tiebas.addAll(lj4);
		}
		if(curpn == null) {
			if(hasMore.equals("1")) {
				page++;
				this.getTbsByUid(uid, tiebas, page, curpn);
			}
		}
	}
	
	
	/**
	 * 获取我喜欢的贴吧（不带分页参数）
	 * @param bduss
	 * @param stoken
	 */
	public List<MyTB> getMyLikedTB(String bduss, String stoken){
		List<MyTB> list = new ArrayList<MyTB>();
		this.getMyLikedTB(bduss, stoken, list, "1");
		return list;
	}
	
	/**
	 * 获取用户信息（user_portrait）
	 * @param bduss
	 * @param stoken
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getUserInfo(String bduss, String stoken){
		try {
			HttpResponse response = hk.execute(Constants.USER_INFO_GET_URL, createCookie(bduss, stoken));
			String result = EntityUtils.toString(response.getEntity());
            if("0".equals(JsonKit.getInfo("no", result).toString())){
            	return (Map<String, Object>) JsonKit.getInfo("data", result);
            }
        } catch (Exception e) {
        	logger.error(e.getMessage(), e);
        }
		return null;
	}
	
	/**
	 * 获取我喜欢的贴吧
	 * @param bduss
	 * @param stoken
	 * @param list
	 * @param fn
	 * @return
	 */
	public void getMyLikedTB(String bduss, String stoken, List<MyTB> list, String fn){
		try {
			HttpResponse response = hk.execute(Constants.MY_LIKE_URL+"?pn="+fn, createCookie(bduss,stoken));
			String result = EntityUtils.toString(response.getEntity());
			Pattern pattern = Pattern.compile("<tr><td>.+?</tr>");
			Matcher matcher = pattern.matcher(result);
			while(matcher.find()){
				Document doc = Jsoup.parse(matcher.group());
				Elements link  = doc.children();
				for (Element element : link) {
					MyTB tb = new MyTB();
					String ex = element.select(".cur_exp").first().text();//当前经验
					String lv = element.select(".like_badge_lv").first().text();//等级
					String lvName = element.select(".like_badge_title").first().html();//等级名称
					String fid = element.select("span").last().attr("balvid");//贴吧ID（签到关键参数）
					String tbName = element.select("a").first().text();//贴吧名称
					//String url = TIEBA_GET_URL +  URLDecoder.decode(element.select("a").first().attr("href"),"utf-8");//贴吧地址
					String url = Constants.TIEBA_GET_URL + element.select("a").first().attr("href");//贴吧地址
					tb.setEx(Integer.parseInt(ex));
					tb.setFid(Integer.parseInt(fid));
					tb.setTbName(tbName);
					tb.setUrl(url);
					tb.setLv(Integer.parseInt(lv));
					tb.setLvName(lvName);
					list.add(tb);
				}
			}
			Document allDoc = Jsoup.parse(result);
			Elements el = allDoc.getElementsByClass("current");
			for (Element element : el) {
				if(element.nextElementSibling() != null){
					String nextFn = element.nextElementSibling().text();
					getMyLikedTB(bduss, stoken, list, nextFn);
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 获取用户头像URL
	 * @param username
	 * @return
	 */
	public String getHeadImg(String username){
		try {
			HttpResponse response = hk.execute(Constants.NEW_HEAD_URL + username + "&ie=utf-8&fr=pb&ie=utf-8");
			String result = EntityUtils.toString(response.getEntity());
			Document doc = Jsoup.parse(result);
			Elements link  = doc.getElementsByAttributeValue("class", "userinfo_head");
			return link.select("img").attr("src");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	/**
	 * 文库签到
	 * @param bduss
	 * @return
	 * @throws Exception
	 */
	public String wenKuSign(String bduss){
		try {
			HashMap<String, Header> headers = new HashMap<String, Header>();
			headers.put("Host", new BasicHeader("Host", "wenku.baidu.com"));
			headers.put("Referer", new BasicHeader("Referer", "https://wenku.baidu.com/task/browse/daily"));
			HttpResponse response = hk.execute(Constants.WENKU_SIGN_URL, createCookie(bduss), headers);
			String result = EntityUtils.toString(response.getEntity());
			System.out.println(result);
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 知道签到
	 * @param bduss
	 * @return
	 */
	public String zhiDaoSign(String bduss){
		try {
			//1.获取stoken
			HttpResponse response = hk.execute(Constants.ZHHIDAO_HOME_URL,createCookie(bduss));
			String stoken = "";
			InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
			char[] buff = new char[1024];
			int length = 0;
			while ((length = reader.read(buff)) != -1) {
				String x = new String(buff, 0, length);
				if(x.contains("stoken")){
					stoken = StrKit.substring(x, "stoken\":\"", "\"");
					break;
				}
			}
			//2.调用签到接口签到
			HashMap<String, Header> headers = new HashMap<String, Header>();
			List<NameValuePair> list = new ArrayList<NameValuePair>();
		    list.add(new BasicNameValuePair("cm", "100509"));
		    list.add(new BasicNameValuePair("stoken", stoken));
		    list.add(new BasicNameValuePair("utdata", "52,52,15,5,9,12,9,52,12,4,15,13,17,12,13,5,13,"+System.currentTimeMillis()));
			HttpResponse res = hk.execute(Constants.ZHHIDAO_API_POST_URL, createCookie(bduss), list, headers);
			String result = EntityUtils.toString(res.getEntity());
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 获取贴吧首页帖子tid列表
	 * @param tbName
	 * @param replyNum 定义标志 根据回复数筛选（回复为0的帖子，抢二楼专用）
	 * @return 帖子tid 数组
	 * 帖子链接：https://tieba.baidu.com/p/  + tid
	 */
	public List<String> getIndexTList(String tbName, Integer replyNum){
		List<String> list = new ArrayList<String>();
		try {
			HttpResponse response = hk.execute(Constants.TIEBA_GET_URL + "/f?kw=" + tbName + "&fr=index");
			String result = EntityUtils.toString(response.getEntity());
			if(StrKit.notBlank(result) && response.getStatusLine().getStatusCode() == 200){
				Document doc_thread = Jsoup.parse(result);
				//解析出帖子code块
				String tcode = doc_thread.getElementById("pagelet_html_frs-list/pagelet/thread_list")
								.html()
								.replace("<!--", "")
								.replace("-->", "");
				//放入新的body解析
				Document doc = Jsoup.parseBodyFragment(tcode);
				Elements link  = doc.getElementsByAttributeValue("class", "j_th_tit "); //帖子链接（获取tid）
				Elements data  = doc.getElementsByAttributeValueMatching("class", "j_thread_list.* clearfix"); //回复数,是否置顶 data-field
				for (int i = 0; i < link.size(); i++) {
					Element element = link.get(i);
					Integer reply= (Integer) JsonKit.getInfo("reply_num",data.get(i).attr("data-field"));
					Object isTop = JsonKit.getInfo("is_top",data.get(i).attr("data-field"));
					if(isTop != null && ("1".equals(isTop.toString()) || "true".equals(isTop.toString()))){//是置顶贴，默认不回复 所以在这里过滤掉
						continue;
					}
					if(replyNum != null){
						if(reply.intValue() == replyNum.intValue()){
							list.add(element.attr("href").substring(3));
						}
					}else{
						list.add(element.attr("href").substring(3));
					}
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return list;
	}
	
	/**
	 * 获取贴吧首页帖子tid列表
	 * @param tbName
	 * @return 帖子tid 数组
	 * 帖子链接：https://tieba.baidu.com/p/  + tid
	 */
	public List<String> getIndexTList(String tbName){
		return this.getIndexTList(tbName, null);
	}
	
	/**
	 * 获取贴吧fid
	 * @param tbName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String getFid(String tbName){
		String fid = "";
		try {
			HttpResponse response = hk.execute(Constants.TIEBA_FID + tbName);
			String result = EntityUtils.toString(response.getEntity());
			int code = Integer.parseInt(JsonKit.getInfo("no", result).toString());
			if(code == 0) {
				Map<String, Object> data = (Map<String, Object>) JsonKit.getInfo("data", result);
				fid = data.get("fid").toString();
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return fid;
	}
	
	/**
	 * 
	 * @param bduss
	 * @param tid ---> thread_id (getMsg可以获取)
	 * @param pid ---> post_id (getMsg可以获取)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String floorpid(String bduss, String tid, String pid){
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "wappc_1450693793907_490"));
			list.add(new BasicNameValuePair("_client_type", "2"));
			list.add(new BasicNameValuePair("_client_version", "5.0.0"));
			list.add(new BasicNameValuePair("_phone_imei", "642b43b58d21b7a5814e1fd41b08e2a6"));
			list.add(new BasicNameValuePair("kz", tid));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("spid", pid));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			String signStr = "";
			for (NameValuePair nameValuePair : list) {
				signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
			}
			signStr += "tiebaclient!!!";
			list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
			HttpResponse response = hk.execute(Constants.FLOR_PID, createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			String error_code = (String) JsonKit.getInfo("error_code", result);
			if(error_code.equals("0")) {
				Map<String, Object> map = (Map<String, Object>) JsonKit.getInfo("post", result);
				return map.get("id").toString();
			}
		} catch (Exception e) {
		}
		return null;
	}
	
	/**
	 * 回帖
	 * @param bduss
	 * @param tid 帖子id
	 * @param tbName 贴吧名称
	 * @param content 回复内容
	 * @param clientType 模拟的客户端类型
	 * @return 操作结果
	 */
	public String reply(String bduss, String tid, String tbName, String content, Integer clientType){
		String msg = "";
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			if(clientType == 0){//随机选择一种方式
				ClientType[] arr = ClientType.values();
				Random random= new Random();
				int  num = random.nextInt(arr.length);
				clientType = arr[num].getCode();
			}
			list.add(new BasicNameValuePair("_client_id", "wappc_1450693793907_490"));
			list.add(new BasicNameValuePair("_client_type", clientType.toString()));
			list.add(new BasicNameValuePair("_client_version", "6.2.2"));
			list.add(new BasicNameValuePair("_phone_imei", "864587027315606"));
			list.add(new BasicNameValuePair("anonymous", "0"));
			list.add(new BasicNameValuePair("content", content));
			list.add(new BasicNameValuePair("fid", getFid(tbName)));
			list.add(new BasicNameValuePair("kw", tbName));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			list.add(new BasicNameValuePair("tid", tid));
			list.add(new BasicNameValuePair("quote_id", "118966657656"));
			list.add(new BasicNameValuePair("title", ""));
			String signStr = "";
			for (NameValuePair nameValuePair : list) {
				signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
			}
			signStr += "tiebaclient!!!";
			list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
			HttpResponse response = hk.execute(Constants.REPLY_POST_URL, createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			String code = (String) JsonKit.getInfo("error_code", result);
			msg = (String) JsonKit.getInfo("msg", result);
			if("0".equals(code)){//回帖成功
				return "回帖成功";
			} else {
				return "回帖失败，错误代码："+code+" "+ (String) JsonKit.getInfo("error_msg", result);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		
		return StrKit.notBlank(msg)?msg:"回帖失败";
	}
	
	/**
	 * 楼中楼回复
	 * @param bduss
	 * @param tid 帖子id
	 * @param tbName 贴吧名称
	 * @param content 回帖内容
	 * @param clientType 模拟客户端类型0，为随机
	 * @param pid 回复楼层id
	 * @return
	 */
	public String replyFloor(String bduss, String tid, String tbName, String content, Integer clientType, String pid){
		String msg = "";
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			if(clientType == 0){//随机选择一种方式
				ClientType[] arr = ClientType.values();
				Random random= new Random();
				int  num = random.nextInt(arr.length);
				clientType = arr[num].getCode();
			}
			list.add(new BasicNameValuePair("_client_id", "wappc_1450693793907_490"));
			list.add(new BasicNameValuePair("_client_type", clientType.toString()));
			list.add(new BasicNameValuePair("_client_version", "6.5.2"));
			list.add(new BasicNameValuePair("_phone_imei", "864587027315606"));
			list.add(new BasicNameValuePair("anonymous", "1"));
			list.add(new BasicNameValuePair("content", content));
			list.add(new BasicNameValuePair("fid", getFid(tbName)));
			list.add(new BasicNameValuePair("kw", tbName));
			list.add(new BasicNameValuePair("model", "SCH-I959"));
			list.add(new BasicNameValuePair("new_vcode", "1"));
			list.add(new BasicNameValuePair("quote_id", pid));
			list.add(new BasicNameValuePair("tbs", getTbs(bduss)));
			list.add(new BasicNameValuePair("tid", tid));
			list.add(new BasicNameValuePair("vcode_tag", "11"));
			String signStr = "";
			for (NameValuePair nameValuePair : list) {
				signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
			}
			signStr += "tiebaclient!!!";
			list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
			HttpResponse response = hk.execute(Constants.REPLY_POST_URL, createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			String code = (String) JsonKit.getInfo("error_code", result);
			msg = (String) JsonKit.getInfo("msg", result);
			if("0".equals(code)){//回帖成功
				return "回帖成功";
			} else {
				return "回帖失败，错误代码："+code+" "+ (String) JsonKit.getInfo("error_msg", result);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		
		return StrKit.notBlank(msg)?msg:"回帖失败";
	}
	
	/**
	 * 查询艾特/回复 信息
	 * @param bduss
	 * @param type reply or at
	 * @param type pn
	 * @return
	 */
	public List<ReplyInfo> getMsg(String bduss, String type, int pn){
		try {
			List<NameValuePair> list = new ArrayList<NameValuePair>();
			list.add(new BasicNameValuePair("BDUSS", bduss));
			list.add(new BasicNameValuePair("_client_id", "wappc_1450693793907_490"));
			list.add(new BasicNameValuePair("_client_type", "2"));
			list.add(new BasicNameValuePair("_client_version", "6.2.2"));
			list.add(new BasicNameValuePair("_phone_imei", "864587027315606"));
			list.add(new BasicNameValuePair("net_type", "3"));
			list.add(new BasicNameValuePair("pn", pn + ""));
			String signStr = "";
			for (NameValuePair nameValuePair : list) {
				signStr += new Formatter().format("%s=%s", nameValuePair.getName(),nameValuePair.getValue()).toString();
			}
			signStr += "tiebaclient!!!";
			list.add(new BasicNameValuePair("sign", MD5Kit.toMd5(signStr).toUpperCase()));
			HttpResponse response = hk.execute(Constants.TBAT_POST_URL + type+ "me", createCookie(bduss), list);
			String result = EntityUtils.toString(response.getEntity());
			return JSON.parseArray(JsonKit.getInfo(type + "_list", result).toString(), ReplyInfo.class);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	/**
	 * 判断登录状态
	 * @param bduss
	 * @return
	 */
	public boolean islogin(String bduss){
		try {
			String tbs = getTbs(bduss);
			if(!StrKit.isBlank(tbs)) {
				return true;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return false;
		}
		return false;
	}
	/**
	 * 根据bduss和stoken生成cookie
	 * @param bduss
	 * @param stoken
	 */
	public String createCookie(String bduss, String stoken, String ptoken){
		StringBuilder sb = new StringBuilder();
		if(StrKit.isBlank(bduss)){
			return null;
		}else{
			sb.append("BDUSS=");
			sb.append(bduss);
		}
		if(!StrKit.isBlank(stoken)){
			sb.append(";");
			sb.append("STOKEN=");
			sb.append(stoken);
		}
		if(!StrKit.isBlank(ptoken)){
			sb.append(";");
			sb.append("PTOKEN=");
			sb.append(ptoken);
		}
		return sb.toString();
	}
	/**
	 * 根据bduss和stoken生成cookie
	 * @param bduss
	 * @param stoken
	 */
	public String createCookie(String bduss, String stoken){
		return createCookie(bduss, stoken, null);
	}
	/**
	 * 根据bduss生成cookie
	 * @param bduss
	 */
	public String createCookie(String bduss){
		return createCookie(bduss, null, null);
	}
}
