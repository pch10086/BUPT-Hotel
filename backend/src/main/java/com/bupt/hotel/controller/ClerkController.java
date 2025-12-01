package com.bupt.hotel.controller;

import com.bupt.hotel.entity.BillingDetail;
import com.bupt.hotel.entity.BillingRecord;
import com.bupt.hotel.entity.LodgingBill;
import com.bupt.hotel.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clerk")
public class ClerkController {

    @Autowired
    private BillingService billingService;

    @PostMapping("/checkout/ac")
    public BillingRecord checkoutAc(@RequestParam String roomId) {
        return billingService.generateAcBill(roomId);
    }

    @PostMapping("/checkout/lodging")
    public LodgingBill checkoutLodging(@RequestParam String roomId) {
        return billingService.generateLodgingBill(roomId);
    }

    @GetMapping("/details")
    public List<BillingDetail> getDetails(@RequestParam String roomId) {
        return billingService.getDetails(roomId);
    }
}
