// let commonURL = "http://192.168.50.115:8081";
let commonURL = "/api";
// 设置后台服务地址
axios.defaults.baseURL = commonURL;
axios.defaults.timeout = 8000;
// request拦截器，将用户token放入头中
let token = sessionStorage.getItem("token");
axios.interceptors.request.use(
  config => {
    if(token) config.headers['authorization'] = token
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)
axios.interceptors.response.use(function (response) {
  // 判断执行结果
  if (!response.data.success) {
    return Promise.reject(response.data.errorMsg)
  }
  return response.data;
}, function (error) {
  // 一般是服务端异常或者网络异常
  console.log(error)
  if(error.response && error.response.status == 401){
    // 未登录，跳转
    setTimeout(() => {
      location.href = "/login.html"
    }, 200);
    return Promise.reject("请先登录");
  }
  if (error.response && error.response.data && error.response.data.errorMsg) {
    return Promise.reject(error.response.data.errorMsg);
  }
  return Promise.reject("服务器异常");
});
axios.defaults.paramsSerializer = function(params) {
  let p = "";
  Object.keys(params).forEach(k => {
    if(params[k] !== undefined && params[k] !== null && params[k] !== ""){
      p = p + "&" + encodeURIComponent(k) + "=" + encodeURIComponent(params[k])
    }
  })
  return p;
}

const roleNames = {
  STUDENT: "学生",
  ORGANIZER: "活动组织者",
  ADMIN: "管理员"
};

function roleHome(role) {
  if (role === "ORGANIZER") return "/organizer.html";
  if (role === "ADMIN") return "/admin.html";
  return "/";
}

/**
 * 登录接口只签发令牌，真实角色必须再由后端 /user/me 返回。
 * 前端身份选择仅用于入口展示和登录后的身份核对，不能提升权限。
 */
function completeRoleLogin(loginToken, expectedRole) {
  if (!loginToken) return Promise.reject("登录失败，请稍后重试");
  sessionStorage.setItem("token", loginToken);
  token = loginToken;
  return axios.get("/user/me").then(({data: user}) => {
    if (!user || !user.role) throw new Error("无法读取账号身份");
    if (expectedRole && user.role !== expectedRole) {
      const actualRole = roleNames[user.role] || user.role;
      return axios.post("/user/logout").catch(() => null).then(() => {
        sessionStorage.removeItem("token");
        sessionStorage.removeItem("currentUser");
        token = null;
        throw new Error("该账号的实际身份是“" + actualRole + "”，请切换正确入口登录");
      });
    }
    sessionStorage.setItem("currentUser", JSON.stringify(user));
    location.href = roleHome(user.role);
    return user;
  }).catch(error => {
    if (error instanceof Error) return Promise.reject(error.message);
    return Promise.reject(error);
  });
}

/** 校验工作台访问角色；最终权限仍由后端接口再次校验。 */
function requireRole(allowedRoles) {
  return axios.get("/user/me").then(({data: user}) => {
    sessionStorage.setItem("currentUser", JSON.stringify(user));
    if (allowedRoles.indexOf(user.role) === -1) {
      location.href = roleHome(user.role);
      return Promise.reject("当前账号无权访问该页面");
    }
    return user;
  });
}
const util = {
  commonURL,
  getUrlParam(name) {
    let reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
    let r = window.location.search.substr(1).match(reg);
    if (r != null) {
      return decodeURI(r[2]);
    }
    return "";
  },
  formatPrice(val) {
    if (typeof val === 'string') {
      if (isNaN(val)) {
        return null;
      }
      // 价格转为整数
      const index = val.lastIndexOf(".");
      let p = "";
      if (index < 0) {
        // 无小数
        p = val + "00";
      } else if (index === val.length - 2) {
        // 1位小数
        p = val.replace("\.", "") + "0";
      } else {
        // 2位小数
        p = val.replace("\.", "")
      }
      return parseInt(p);
    } else if (typeof val === 'number') {
      if (val === 0) {
        return "0.00";
      }
      const s = val + '';
      if (s.length === 0) {
        return "0.00";
      }
      if (s.length === 1) {
        return "0.0" + val;
      }
      if (s.length === 2) {
        return "0." + val;
      }
      const i = s.indexOf(".");
      if (i < 0) {
        return s.substring(0, s.length - 2) + "." + s.substring(s.length - 2)
      }
      const num = s.substring(0, i) + s.substring(i + 1);
      if (i === 1) {
        // 1位整数
        return "0.0" + num;
      }
      if (i === 2) {
        return "0." + num;
      }
      if (i > 2) {
        return num.substring(0, i - 2) + "." + num.substring(i - 2)
      }
    }
  }
}
