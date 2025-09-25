package com.thunder.matchenginex.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 性能监控页面控制器
 * Performance monitoring page controller
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PerformancePageController {

    /**
     * 性能监控页面
     * Performance monitoring page
     */
    @GetMapping("/performance")
    public String performancePage(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "性能监控");
        model.addAttribute("activeMenu", "performance");
        model.addAttribute("contentTemplate", "pages/performance-minimal");

        // Set default user ID if not in session
        if (session.getAttribute("userId") == null) {
            session.setAttribute("userId", 1);
        }

        return "layout/base";
    }

    /**
     * 性能监控测试页面
     * Performance monitoring test page
     */
    @GetMapping("/test-performance")
    public String testPerformancePage(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "性能监控测试");
        model.addAttribute("activeMenu", "performance");
        model.addAttribute("contentTemplate", "pages/test-performance");

        // Set default user ID if not in session
        if (session.getAttribute("userId") == null) {
            session.setAttribute("userId", 1);
        }

        return "layout/base";
    }
}