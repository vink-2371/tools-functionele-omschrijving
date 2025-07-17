package nl.vink.func_omschr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    
    /**
     * Homepage redirect naar projectenoverzicht
     */
    @GetMapping("/")
    public String home() {
        return "projecten/overzicht";
    }

    /**
     * Index mapping (backup)
     */
    @GetMapping("/index")
    public String index() {
        return "redirect:/projecten";
    }

}
