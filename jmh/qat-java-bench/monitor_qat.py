#!/usr/bin/python3.6

import time
import curses
import subprocess
import re


devices=[]
paths=[]
set_paths=[]
    
def EnableTelemetry():
    
    devices.clear()
    command = "adf_ctl status"
    sp = subprocess.Popen(command,shell=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)

    # Store the return code in rc variable
    rc=sp.wait()

    # Separate the output and error
    # This is similar to Tuple where we store two values to two different variables
    out,err=sp.communicate()
    
    # Split string into list of strings
    output_adf = out.split()

    paths.clear()
    command = 'find /sys/devices/ -name "telemetry"'
    sp = subprocess.Popen(command,shell=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)

    # Store the return code in rc variable
    rc=sp.wait()

    # Separate the output and error.
    # This is similar to Tuple where we store two values to two different variables
    out,err=sp.communicate()

    # Split string into list of strings
    original_array= out.split()     
    output_telem = sorted(original_array, key=lambda x: (x.split(':')[1], 16))

    i = 0
    state = "down"
    name = None
    bus = None
    telemetry_supported = False

    # Build device list from adf_status output
    while i < len(output_adf):
        if "qat_dev" in output_adf[i]:
            name = output_adf[i]
        elif "type:" == output_adf[i]:
            if "4xxx," == output_adf[i+1]:
                telemetry_supported = True 
        elif "bsf:" == output_adf[i]:
            bus = output_adf[i+1][5:7]
        elif "state:" == output_adf[i]:
            if "up" == output_adf[i+1]:
                if telemetry_supported == True:
                    devices.append((name, bus))            

            # Reset variables to ensure we only attempt to enable telemetery on devices that support telemetry and are in up state
            state = "down"
            name = None
            bus = None
            telemetry_supported = False
        i += 1
    
    # Build path list from Telemetry search
    i = 0
    for i in range(len(output_telem)):
        paths.append(output_telem[i])

    # Verify Telemetry paths are part of enabled QAT endpoints
    set_paths.clear()
    i = 0
    for path in paths:
        while i < len(devices):
            if devices[i][1] in path:
                set_paths.append(path)
            i += 1
        i = 0

    if len(set_paths) == 0:
       print("No telemetry supported QAT endpoints found... exiting.")
       quit() 

    # Enable Telemetry for QAT endpoints
    for path in set_paths:
        control_file_name= path + "/control"
        command = "echo 1 > " + control_file_name

        try:
            str(subprocess.check_output(command, shell=True))
        except:
            break

def pbar(window):
    refresh_counter = 0

    while True:

        try:
            refresh_counter += 1

            window.addstr(0, 10, "Intel(R) QuickAssist Device Utilization")
            window.addstr(2, 10, "Device\t%Comp\t%Decomp\t%PKE\t%Cipher\t%Auth\t%UCS\tLatency(ns)")
            window.addstr(3, 10, "=========================================================================")

            count = 0
            for device in devices:
                    
                command = "cat " + set_paths[count] + "/device_data"
                sp = subprocess.Popen(command,shell=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)

                # Store the return code in rc variable
                rc=sp.wait()

                # Separate the output and error
                # This is similar to Tuple where we store two values to two different variables
                out,err=sp.communicate()
                
                # Split string into list of strings
                output = out.split()              
	    
                i = 0
                while i < len(output):
            
                    if "lat_acc_avg" == output[i]:
                        latency = output[i+1]
                    elif "util_cpr0" == output[i]:
                        compression = output[i+1]
                    elif "util_dcpr0" == output[i]:
                        decompression0 = output[i+1]
                    elif "util_dcpr1" == output[i]:
                        decompression1 = output[i+1]
                    elif "util_dcpr2" == output[i]:
                        decompression2 = output[i+1]
                    elif "util_pke0" == output[i]:
                        pke0 = output[i+1]
                    elif "util_pke1" == output[i]:
                        pke1 = output[i+1]
                    elif "util_pke2" == output[i]:
                        pke2 = output[i+1]
                    elif "util_pke3" == output[i]:
                        pke3 = output[i+1]
                    elif "util_pke4" == output[i]:
                        pke4 = output[i+1]
                    elif "util_pke5" == output[i]:
                        pke5 = output[i+1]
                    elif "util_cph0" == output[i]:
                        cph0 = output[i+1]
                    elif "util_cph1" == output[i]:
                        cph1 = output[i+1]
                    elif "util_cph2" == output[i]:
                        cph2 = output[i+1]
                    elif "util_cph3" == output[i]:
                        cph3 = output[i+1]
                    elif "util_ath0" == output[i]:
                        ath0 = output[i+1]
                    elif "util_ath1" == output[i]:
                        ath1 = output[i+1]
                    elif "util_ath2" == output[i]:
                        ath2 = output[i+1]
                    elif "util_ath3" == output[i]:
                        ath3 = output[i+1]
                    elif "util_ucs0" == output[i]:
                        ucs0 = output[i+1]
                    elif "util_ucs1" == output[i]:
                        ucs1 = output[i+1]
                    i += 1

                decompress_utilization = int(decompression0) + int(decompression1) + int(decompression2)
                if decompress_utilization > 0:
                    decompress_utilization = decompress_utilization / 3
                    decompress_utilization = round(decompress_utilization)
                pke_utilization = int(pke0) + int(pke1) + int(pke2) + int(pke3) + int(pke4)+ int(pke5)
                if pke_utilization > 0:
                    pke_utilization = pke_utilization / 6
                    pke_utilization = round(pke_utilization)
                cph_utilization = int(cph0) + int(cph1) + int(cph2) + int(cph3) 
                if cph_utilization > 0:
                    cph_utilization = cph_utilization / 4
                    cph_utilization = round(cph_utilization)
                ath_utilization = int(ath0) + int(ath1) + int(ath2) + int(ath3) 
                if ath_utilization > 0:
                    ath_utilization = ath_utilization / 4
                    ath_utilization = round(ath_utilization)
                usc_utilization = int(ucs0) + int(ucs1)
                if usc_utilization > 0:
                    usc_utilization = usc_utilization / 2
                    usc_utilization = round(usc_utilization)
                if int(latency) == 0:
                    window.addstr(4+count, 10, device[0] + '\t0\t0\t0\t0\t0\t00                 ') 
                 
                window.addstr(4+count, 10, device[0] + '\t' + compression + '\t' + str(decompress_utilization) + '\t' + str(pke_utilization) + '\t' + str(cph_utilization) + '\t' + str(ath_utilization) + '\t' + str(usc_utilization) + '\t'+ latency)
                count += 1

        
            window.addstr(4+count, 10, "=========================================================================")
            window.refresh()
            time.sleep(2)
            if refresh_counter % 5 == 0:
                window.clear()
                EnableTelemetry()

        except KeyboardInterrupt:
            break
        except:
            break


if __name__ == "__main__":
    EnableTelemetry()
    curses.wrapper(pbar)

