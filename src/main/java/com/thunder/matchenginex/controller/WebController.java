package com.thunder.matchenginex.controller;

import com.thunder.matchenginex.constant.TradingConstants;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "首页");
        model.addAttribute("activeMenu", "home");
        model.addAttribute("contentTemplate", "pages/home");

        // Set default user ID if not in session
        if (session.getAttribute("userId") == null) {
            session.setAttribute("userId", 1);
        }

        return "layout/base";
    }

    @GetMapping("/trading")
    public String trading(@RequestParam(value = "symbol", defaultValue = TradingConstants.DEFAULT_SYMBOL) String symbol,
                         Model model, HttpSession session) {
        model.addAttribute("pageTitle", "交易 - " + symbol);
        model.addAttribute("activeMenu", "trading");
        model.addAttribute("contentTemplate", "pages/trading");
        model.addAttribute("symbol", symbol);

        // Set default user ID if not in session
        if (session.getAttribute("userId") == null) {
            session.setAttribute("userId", 1);
        }

        return "layout/base";
    }

    @GetMapping("/account")
    public String account(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "账户管理");
        model.addAttribute("activeMenu", "account");
        model.addAttribute("contentTemplate", "pages/account");

        // Set default user ID if not in session
        if (session.getAttribute("userId") == null) {
            session.setAttribute("userId", 1);
        }

        return "layout/base";
    }

    @PostMapping("/api/session/user")
    @ResponseBody
    public SessionResponse updateSessionUser(@RequestBody UserIdRequest request, HttpSession session) {
        session.setAttribute("userId", request.getUserId());
        return new SessionResponse(true, "User ID updated successfully");
    }

    // Request/Response classes
    public static class UserIdRequest {
        private Integer userId;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }
    }

    public static class SessionResponse {
        private boolean success;
        private String message;

        public SessionResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}