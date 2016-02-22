/*
 * Copyright 2012 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.net.SocketAddress;
import java.util.regex.Pattern;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.Context;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.helper.PatternUtil;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Event;
import org.traccar.model.Position;

public class Gl200ProtocolDecoder extends BaseProtocolDecoder {

    public Gl200ProtocolDecoder(Gl200Protocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN_HBD = new PatternBuilder()
            .text("+ACK:GTHBD,")
            .number("([0-9A-Z]{2}xxxx),")
            .any().text(",")
            .number("(xxxx)")
            .text("$").optional()
            .compile();

    private static final Pattern PATTERN_INF = new PatternBuilder()
            .text("+RESP:GTINF,")
            .number("[0-9A-Z]{2}xxxx,")          // protocol version
            .number("(d{15}),")                  // imei
            .expression("[0-9A-Z]{17},")         // vin
            .expression("[^,]{0,20},")           // device name
            .number("(xx),")                     // state
            .expression("[0-9F]{20},")           // iccid
            .number("d{1,2},")
            .number("d{1,2},")
            .expression("[01],")
            .number("(d{1,5}),")                 // power
            .text(",")
            .number("(d+.d+),")                  // battery
            .expression("([01]),")               // charging
            .expression("[01],")
            .text(",,")
            .number("d{14},")                    // last fix time
            .text(",,,,,")
            .number("[-+]dddd,")                 // timezone
            .expression("[01],")                 // daylight saving
            .number("(dddd)(dd)(dd)")            // date
            .number("(dd)(dd)(dd),")             // time
            .number("(xxxx)")                    // counter
            .text("$").optional()
            .compile();

    private static final Pattern PATTERN_OBD = new PatternBuilder()
            .text("+RESP:GTOBD,")
            .number("[0-9A-Z]{2}xxxx,")          // protocol version
            .number("(d{15}),")                  // imei
            .expression("(?:[0-9A-Z]{17})?,")    // vin
            .expression("[^,]{0,20},")           // device name
            .expression("[01],")                 // report type
            .number("x{1,8},")                   // report mask
            .expression("(?:[0-9A-Z]{17})?,")    // vin
            .number("[01],")                     // obd connect
            .number("(?:d{1,5})?,")              // obd voltage
            .number("(?:x{8})?,")                // support pids
            .number("(d{1,5})?,")                // engine rpm
            .number("(d{1,3})?,")                // speed
            .number("(-?d{1,3})?,")              // coolant temp
            .number("(d+.?d*|Inf|NaN)?,")        // fuel consumption
            .number("(d{1,5})?,")                // dtcs cleared distance
            .number("(?:d{1,5})?,")
            .expression("([01])?,")              // obd connect
            .number("(d{1,3})?,")                // number of dtcs
            .number("(x*),")                     // dtcs
            .number("(d{1,3})?,")                // throttle
            .number("(?:d{1,3})?,")              // engine load
            .number("(d{1,3})?,")                // fuel level
            .number("(d+),")                     // odometer
            .number("(?:d{1,2})?,")              // gps accuracy
            .number("(d{1,3}.d)?,")              // speed
            .number("(d{1,3})?,")                // course
            .number("(-?d{1,5}.d)?,")            // altitude
            .number("(-?d{1,3}.d{6})?,")         // longitude
            .number("(-?d{1,2}.d{6})?,")         // latitude
            .number("(dddd)(dd)(dd)")            // date
            .number("(dd)(dd)(dd)").optional(2)  // time
            .text(",")
            .number("(0ddd)?,")                  // mcc
            .number("(0ddd)?,")                  // mnc
            .number("(xxxx)?,")                  // lac
            .number("(xxxx)?,")                  // cell
            .number("d*,")                       // reserved
            .number("(d{1,7}.d)?,")              // odometer
            .number("(dddd)(dd)(dd)")            // date
            .number("(dd)(dd)(dd)").optional(2)  // time
            .text(",")
            .number("(xxxx)")                    // count number
            .text("$").optional()
            .compile();

    private static final Pattern PATTERN = new PatternBuilder()
            .text("+").expression("(?:RESP|BUFF)").text(":")
            .expression("GT...,")
            .number("(?:[0-9A-Z]{2}xxxx)?,")     // protocol version
            .number("(d{15}),")                  // imei
            .expression("[^,]*,")                // device name
            .number("d,")                        // report id
            .number("d,")                        // report type
            .number("d{1,2},")                   // report number
            .number("(?:d{1,2})?,")              // gps accuracy
            .number("(d{1,3}.d)?,")              // speed
            .number("(d{1,3})?,")                // course
            .number("(-?d{1,5}.d)?,")            // altitude
            .number("(-?d{1,3}.d{6})?,")         // longitude
            .number("(-?d{1,2}.d{6})?,")         // latitude
            .number("(dddd)(dd)(dd)")            // date
            .number("(dd)(dd)(dd)").optional(2)  // time
            .text(",")
            .number("(0ddd)?,")                  // mcc
            .number("(0ddd)?,")                  // mnc
            .number("(xxxx)?,")                  // lac
            .number("(xxxx)?,")                  // cell
            .number("d*,")                       // reserved
            .number("(d{1,3})?,")                // battery
            .number("(dddd)(dd)(dd)")            // date
            .number("(dd)(dd)(dd)").optional(2)  // time
            .text(",")
            .number("(xxxx)")                    // count number
            .text("$").optional()
            .compile();

    private static final Pattern PATTERN_BACKUP = new PatternBuilder()
            .text("+").expression("(?:RESP|BUFF)").text(":")
            .expression("GT...,")
            .number("(?:[0-9A-Z]{2}xxxx)?,")     // protocol version
            .number("(d{15}|x{14}),")            // imei
            .any()
            .number("(d{1,3}.d)?,")              // speed
            .number("(d{1,3})?,")                // course
            .number("(-?d{1,5}.d)?,")            // altitude
            .number("(-?d{1,3}.d{6}),")          // longitude
            .number("(-?d{1,2}.d{6}),")          // latitude
            .number("(dddd)(dd)(dd)")            // date
            .number("(dd)(dd)(dd)")              // time
            .text(",")
            .any()
            .number("(xxxx)")                    // count number
            .text("$").optional()
            .compile();

    private Position decodeHbd(Channel channel, SocketAddress remoteAddress, String sentence) {
        Parser parser = new Parser(PATTERN_HBD, sentence);
        if (parser.matches() && channel != null) {
            channel.write("+SACK:GTHBD," + parser.next() + "," + parser.next() + "$", remoteAddress);
        }
        return null;
    }

    private Position decodeInf(Channel channel, SocketAddress remoteAddress, String sentence) {
        Parser parser = new Parser(PATTERN_INF, sentence);
        if (!parser.matches()) {
            return null;
        }

        Position position = new Position();
        position.setProtocol(getProtocolName());

        if (!identify(parser.next(), channel, remoteAddress)) {
            return null;
        }
        position.setDeviceId(getDeviceId());

        position.set(Event.KEY_STATUS, parser.next());
        position.set(Event.KEY_POWER, parser.next());
        position.set(Event.KEY_BATTERY, parser.next());
        position.set(Event.KEY_CHARGE, parser.next());

        DateBuilder dateBuilder = new DateBuilder()
                .setDate(parser.nextInt(), parser.nextInt(), parser.nextInt())
                .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());

        getLastLocation(position, dateBuilder.getDate());

        position.set(Event.KEY_INDEX, parser.next());

        return position;
    }

    private Position decodeObd(Channel channel, SocketAddress remoteAddress, String sentence) {

        String x = PatternUtil.checkPattern(PATTERN_OBD.pattern(), sentence);

        Parser parser = new Parser(PATTERN_OBD, sentence);
        if (!parser.matches()) {
            return null;
        }

        Position position = new Position();
        position.setProtocol(getProtocolName());

        if (!identify(parser.next(), channel, remoteAddress)) {
            return null;
        }
        position.setDeviceId(getDeviceId());

        position.set(Event.KEY_RPM, parser.next());
        position.set(Event.KEY_OBD_SPEED, parser.next());
        position.set(Event.PREFIX_TEMP + 1, parser.next());
        position.set("fuel-consumption", parser.next());
        position.set("dtcs-cleared-distance", parser.next());
        position.set("odb-connect", parser.next());
        position.set("dtcs-number", parser.next());
        position.set("dtcs-codes", parser.next());
        position.set(Event.KEY_THROTTLE, parser.next());
        position.set(Event.KEY_FUEL, parser.next());
        position.set(Event.KEY_OBD_ODOMETER, parser.next());

        position.setSpeed(UnitsConverter.knotsFromKph(parser.nextDouble()));
        position.setCourse(parser.nextDouble());
        position.setAltitude(parser.nextDouble());

        if (parser.hasNext(8)) {
            position.setValid(true);
            position.setLongitude(parser.nextDouble());
            position.setLatitude(parser.nextDouble());

            DateBuilder dateBuilder = new DateBuilder()
                    .setDate(parser.nextInt(), parser.nextInt(), parser.nextInt())
                    .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
            position.setTime(dateBuilder.getDate());
        } else {
            getLastLocation(position, null);
        }

        if (parser.hasNext(4)) {
            position.set(Event.KEY_MCC, parser.nextInt());
            position.set(Event.KEY_MNC, parser.nextInt());
            position.set(Event.KEY_LAC, parser.nextInt(16));
            position.set(Event.KEY_CID, parser.nextInt(16));
        }

        position.set(Event.KEY_ODOMETER, parser.next());

        if (parser.hasNext(6)) {
            DateBuilder dateBuilder = new DateBuilder()
                    .setDate(parser.nextInt(), parser.nextInt(), parser.nextInt())
                    .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
            if (!position.getOutdated() && position.getFixTime().after(dateBuilder.getDate())) {
                position.setTime(dateBuilder.getDate());
            }
        }

        return position;
    }

    private Position decodeOther(Channel channel, SocketAddress remoteAddress, String sentence) {
        Pattern pattern = PATTERN;
        Parser parser = new Parser(pattern, sentence);
        if (!parser.matches()) {
            pattern = PATTERN_BACKUP;
            parser = new Parser(pattern, sentence);
            if (!parser.matches()) {
                return null;
            }
        }

        Position position = new Position();
        position.setProtocol(getProtocolName());

        if (!identify(parser.next(), channel, remoteAddress)) {
            return null;
        }
        position.setDeviceId(getDeviceId());

        position.setSpeed(UnitsConverter.knotsFromKph(parser.nextDouble()));
        position.setCourse(parser.nextDouble());
        position.setAltitude(parser.nextDouble());

        if (parser.hasNext(8)) {
            position.setValid(true);
            position.setLongitude(parser.nextDouble());
            position.setLatitude(parser.nextDouble());

            DateBuilder dateBuilder = new DateBuilder()
                    .setDate(parser.nextInt(), parser.nextInt(), parser.nextInt())
                    .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
            position.setTime(dateBuilder.getDate());
        } else {
            getLastLocation(position, null);
        }

        if (pattern == PATTERN) {
            if (parser.hasNext(4)) {
                position.set(Event.KEY_MCC, parser.nextInt());
                position.set(Event.KEY_MNC, parser.nextInt());
                position.set(Event.KEY_LAC, parser.nextInt(16));
                position.set(Event.KEY_CID, parser.nextInt(16));
            }

            position.set(Event.KEY_BATTERY, parser.next());

            if (parser.hasNext(6)) {
                DateBuilder dateBuilder = new DateBuilder()
                        .setDate(parser.nextInt(), parser.nextInt(), parser.nextInt())
                        .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
                if (!position.getOutdated() && position.getFixTime().after(dateBuilder.getDate())) {
                    position.setTime(dateBuilder.getDate());
                }
            }
        }

        if (Context.getConfig().getBoolean(getProtocolName() + ".ack") && channel != null) {
            channel.write("+SACK:" + parser.next() + "$", remoteAddress);
        }

        return position;
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        String sentence = (String) msg;

        int typeIndex = sentence.indexOf(":GT");
        if (typeIndex < 0) {
            return null;
        }

        switch (sentence.substring(typeIndex + 3, typeIndex + 6)) {
            case "HBD":
                return decodeHbd(channel, remoteAddress, sentence);
            case "INF":
                return decodeInf(channel, remoteAddress, sentence);
            case "OBD":
                return decodeObd(channel, remoteAddress, sentence);
            default:
                return decodeOther(channel, remoteAddress, sentence);
        }
    }

}
