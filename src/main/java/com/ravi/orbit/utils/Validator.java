package com.ravi.orbit.utils;

import com.ravi.orbit.dto.AuthDTO;
import com.ravi.orbit.dto.SellerDTO;
import com.ravi.orbit.dto.UserDTO;

public class Validator {

    public static void validateUserSignup(UserDTO request) {
//        CommonValidator.validatePhoneNo(request.getPhone());
        CommonValidator.validateEmail(request.getEmail());
        CommonValidator.validateDevice(request.getDeviceId(), request.getDeviceName());
    }

    public static void validateSellerSignup(UserDTO request) {
        CommonValidator.validatePhoneNo(request.getPhone());
        validateUserSignup(request);
    }

    public static void validateLogin(AuthDTO request) {
        CommonValidator.validateAuth(request);
        CommonValidator.validateDevice(request.getDeviceId(), request.getDeviceName());
    }

}
