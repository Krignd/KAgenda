// Independent KAgenda HFUT EAMS 5.0 port. Fixed Apache-2.0/MIT protocol sources, file dates, hashes and full notices: academic/school-provenance.json.
// DSH public-DTO normalizer draft reviewed and corrected locally; host implemented locally. Real school accounts remain unverified.
/*
 * Independent, pure-data normalizer for the public HFUT course-table contract.
 *
 * No DOM, cookie, storage, network, host bridge, framework, or project code is
 * used.  The caller supplies every value.  Invalid input always throws; this
 * module never returns a partial result.
 */
(function (global) {
    'use strict';

    var MAX_STRING = 2048;
    var MAX_NAME = 256;
    var MAX_CODE = 128;
    var MAX_LIST = 5000;
    var MAX_WEEKS = 40;
    var MAX_TEACHER_ASSIGNMENTS = 500;
    var DAY_MS = 86400000;

    var INPUT_KEYS = [
        'termData', 'datumData', 'layoutData', 'semesterId', 'studentId',
        'week1Monday', 'totalWeeks'
    ];
    var TERM_KEYS = [
        'lessonIds', 'lessons', 'timeTableLayoutId', 'weekIndices', 'currentWeek'
    ];
    var TABLE_RESULT_KEYS = ['lessonList', 'scheduleList', 'scheduleGroupList'];
    var PAGING_KEYS = new Set([
        'page', 'pageno', 'pagenum', 'pagenumber', 'pagesize', 'pagecount',
        'total', 'totalcount', 'totalpages', 'totalelements', 'hasnext',
        'hasprevious', 'nextpage', 'prevpage', 'cursor', 'offset', 'limit'
    ]);

    class HFUTNormalizeError extends Error {
        constructor(path, message) {
            super(path + ': ' + message);
            this.name = 'HFUTNormalizeError';
            this.code = 'HFUT_NORMALIZE_INVALID';
            this.path = path;
        }
    }

    function fail(path, message) {
        throw new HFUTNormalizeError(path, message);
    }

    function isPlainObject(value) {
        if (value === null || typeof value !== 'object' || Array.isArray(value)) {
            return false;
        }
        var proto = Object.getPrototypeOf(value);
        return proto === Object.prototype || proto === null;
    }

    function assertPlainObject(value, path) {
        if (!isPlainObject(value)) {
            fail(path, 'must be a plain object');
        }
        return value;
    }

    function hasOwn(obj, key) {
        return Object.prototype.hasOwnProperty.call(obj, key);
    }

    function assertExactKeys(obj, keys, path) {
        var allowed = new Set(keys);
        Object.keys(obj).forEach(function (key) {
            if (!allowed.has(key)) {
                if (PAGING_KEYS.has(key.toLowerCase())) {
                    fail(path + '.' + key, 'pagination metadata is not supported');
                }
                fail(path + '.' + key, 'unexpected field');
            }
        });
        keys.forEach(function (key) {
            if (!hasOwn(obj, key)) {
                fail(path + '.' + key, 'is required');
            }
        });
    }

    function hasNonEmptyArray(value, seen) {
        if (Array.isArray(value)) {
            return value.length > 0;
        }
        if (isPlainObject(value)) {
            if (seen.has(value)) {
                return false;
            }
            seen.add(value);
            var keys = Object.keys(value);
            for (var i = 0; i < keys.length; i += 1) {
                if (hasNonEmptyArray(value[keys[i]], seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Nested response records are allowed to carry harmless scalar metadata
     * that is not part of the captured model, but an unexpected non-empty
     * array (a second page/list) is never silently ignored.
     */
    function assertRecord(obj, path, allowedKeys) {
        var allowed = new Set(allowedKeys);
        Object.keys(obj).forEach(function (key) {
            if (allowed.has(key)) {
                return;
            }
            if (PAGING_KEYS.has(key.toLowerCase())) {
                fail(path + '.' + key, 'pagination metadata is not supported');
            }
            if ((isPlainObject(obj[key]) && Object.keys(obj[key]).length > 0) || /^(?:error|success|status|code|err_code|message|msg)$/i.test(key) || hasNonEmptyArray(obj[key], new Set())) {
                fail(path + '.' + key, 'unexpected non-empty array');
            }
        });
    }

    function assertStringBounds(value, path, seen) {
        if (typeof value === 'string') {
            if (value.length > MAX_STRING) {
                fail(path, 'string exceeds the ' + MAX_STRING + ' character limit');
            }
            return;
        }
        if (Array.isArray(value)) {
            for (var i = 0; i < value.length; i += 1) {
                assertStringBounds(value[i], path + '[' + i + ']', seen);
            }
            return;
        }
        if (isPlainObject(value)) {
            if (seen.has(value)) {
                fail(path, 'cyclic data is not supported');
            }
            seen.add(value);
            Object.keys(value).forEach(function (key) {
                assertStringBounds(value[key], path + '.' + key, seen);
            });
            seen.delete(value);
        }
    }

    function requireArray(value, path, min, max) {
        if (!Array.isArray(value)) {
            fail(path, 'must be an array');
        }
        if (value.length < min) {
            fail(path, 'must contain at least ' + min + ' item(s)');
        }
        if (value.length > max) {
            fail(path, 'must contain at most ' + max + ' item(s)');
        }
        return value;
    }

    function requireString(value, path, max) {
        if (typeof value !== 'string') {
            fail(path, 'must be a string');
        }
        if (value.length > max) {
            fail(path, 'must be at most ' + max + ' characters');
        }
        return value;
    }

    function requireNonEmptyString(value, path, max) {
        var text = requireString(value, path, max);
        if (text.trim().length === 0) {
            fail(path, 'must be a non-empty string');
        }
        return text;
    }

    function optionalString(value, path, max) {
        if (value === null || value === undefined) {
            return null;
        }
        return requireString(value, path, max);
    }

    function requireNonNegativeInteger(value, path, max) {
        if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value)) {
            fail(path, 'must be a finite integer');
        }
        if (value < 0) {
            fail(path, 'must be non-negative');
        }
        if (value > max) {
            fail(path, 'must be at most ' + max);
        }
        return value;
    }

    function requirePositiveInteger(value, path, max) {
        if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value)) {
            fail(path, 'must be a finite positive integer');
        }
        if (value <= 0) {
            fail(path, 'must be a positive integer');
        }
        if (value > max) {
            fail(path, 'must be at most ' + max);
        }
        return value;
    }

    function optionalInteger(value, path, max) {
        if (value === null || value === undefined) {
            return null;
        }
        return requireNonNegativeInteger(value, path, max);
    }

    function requireFiniteNumber(value, path, min, max) {
        if (typeof value !== 'number' || !Number.isFinite(value)) {
            fail(path, 'must be a finite number');
        }
        if (value < min || value > max) {
            fail(path, 'must be between ' + min + ' and ' + max);
        }
        return value;
    }

    function pad2(value) {
        return String(value).padStart(2, '0');
    }

    function daysInMonth(year, month) {
        var days = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
        if (month === 2 && ((year % 4 === 0 && year % 100 !== 0) || year % 400 === 0)) {
            return 29;
        }
        return days[month - 1];
    }

    function parseISODate(value, path) {
        requireString(value, path, 10);
        var match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
        if (!match) {
            fail(path, 'must be exactly YYYY-MM-DD');
        }
        var year = Number(match[1]);
        var month = Number(match[2]);
        var day = Number(match[3]);
        if (year < 1000 || year > 9999 || month < 1 || month > 12) {
            fail(path, 'is not a real calendar date');
        }
        if (day < 1 || day > daysInMonth(year, month)) {
            fail(path, 'is not a real calendar date');
        }
        var ms = Date.UTC(year, month - 1, day);
        var date = new Date(ms);
        if (date.getUTCFullYear() !== year ||
            date.getUTCMonth() !== month - 1 ||
            date.getUTCDate() !== day) {
            fail(path, 'is not a real calendar date');
        }
        var jsDay = date.getUTCDay();
        return {
            text: value,
            ms: ms,
            year: year,
            month: month,
            day: day,
            weekday: jsDay === 0 ? 7 : jsDay
        };
    }

    function parseClockInteger(value, path) {
        if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value)) {
            fail(path, 'must be a finite HHmm integer');
        }
        if (value < 0 || value > 2359) {
            fail(path, 'is outside 0000..2359');
        }
        var hour = Math.floor(value / 100);
        var minute = value % 100;
        if (hour > 23 || minute > 59) {
            fail(path, 'is not a real HHmm time');
        }
        return {
            value: value,
            minuteOfDay: hour * 60 + minute,
            text: pad2(hour) + ':' + pad2(minute)
        };
    }

    function parseClockText(value, path) {
        requireString(value, path, 5);
        var match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(value);
        if (!match) {
            fail(path, 'must be exactly HH:mm');
        }
        return {
            text: value,
            minuteOfDay: Number(match[1]) * 60 + Number(match[2])
        };
    }

    function parseUnitNumber(name, path) {
        requireString(name, path, 32);
        var number = null;
        if (/^[1-9]\d*$/.test(name)) {
            number = Number(name);
        } else {
            var match = /^第([1-9]\d*)(?:小)?节$/.exec(name);
            if (match) {
                number = Number(match[1]);
            }
        }
        if (number === null || !Number.isInteger(number) || number < 1 || number > 24) {
            fail(path, 'must name section 1..24 as "第n节", "第n小节", or "n"');
        }
        return number;
    }

    function multiLanguageName(raw, path) {
        var obj = assertPlainObject(raw, path);
        assertRecord(obj, path, ['nameZh']);
        return requireNonEmptyString(obj.nameZh, path + '.nameZh', MAX_NAME);
    }

    function validateTermTeacherAssignments(raw, path) {
        if (raw === null || raw === undefined) {
            return [];
        }
        var list = requireArray(raw, path, 0, MAX_TEACHER_ASSIGNMENTS);
        return list.map(function (item, index) {
            var itemPath = path + '[' + index + ']';
            var obj = assertPlainObject(item, itemPath);
            assertRecord(obj, itemPath, ['teacher', 'age', 'person']);
            var personName = multiLanguageName(obj.person, itemPath + '.person');
            var age = optionalInteger(obj.age, itemPath + '.age', 200);
            var teacher = null;
            if (obj.teacher !== null && obj.teacher !== undefined) {
                var teacherPath = itemPath + '.teacher';
                var teacherObj = assertPlainObject(obj.teacher, teacherPath);
                assertRecord(teacherObj, teacherPath, ['person', 'title', 'type']);
                teacher = {
                    personName: teacherObj.person === null || teacherObj.person === undefined
                        ? null
                        : multiLanguageName(teacherObj.person, teacherPath + '.person'),
                    title: teacherObj.title === null || teacherObj.title === undefined
                        ? null
                        : multiLanguageName(teacherObj.title, teacherPath + '.title'),
                    type: teacherObj.type === null || teacherObj.type === undefined
                        ? null
                        : multiLanguageName(teacherObj.type, teacherPath + '.type')
                };
            }
            return { personName: personName, age: age, teacher: teacher };
        });
    }

    function validateTableTeacherAssignments(raw, path) {
        var list = requireArray(raw, path, 0, MAX_TEACHER_ASSIGNMENTS);
        return list.map(function (item, index) {
            var itemPath = path + '[' + index + ']';
            var obj = assertPlainObject(item, itemPath);
            assertRecord(obj, itemPath, ['code', 'name', 'age', 'titleName']);
            return {
                code: requireString(obj.code, itemPath + '.code', MAX_CODE),
                name: requireNonEmptyString(obj.name, itemPath + '.name', MAX_NAME),
                age: optionalInteger(obj.age, itemPath + '.age', 200),
                titleName: optionalString(obj.titleName, itemPath + '.titleName', MAX_NAME)
            };
        });
    }

    function validateTermLesson(raw, path, semesterId) {
        var obj = assertPlainObject(raw, path);
        assertRecord(obj, path, [
            'id', 'nameZh', 'remark', 'scheduleText', 'stdCount', 'course',
            'courseType', 'openDepartment', 'examMode', 'scheduleWeeksInfo',
            'planExamWeek', 'teacherAssignmentList', 'semester', 'code'
        ]);

        var id = requirePositiveInteger(obj.id, path + '.id', Number.MAX_SAFE_INTEGER);
        var nameZh = optionalString(obj.nameZh, path + '.nameZh', MAX_NAME);
        var remark = optionalString(obj.remark, path + '.remark', MAX_STRING);

        var scheduleTextPath = path + '.scheduleText';
        var scheduleTextObj = assertPlainObject(obj.scheduleText, scheduleTextPath);
        assertRecord(scheduleTextObj, scheduleTextPath, ['dateTimePlacePersonText']);
        var dateTimePath = scheduleTextPath + '.dateTimePlacePersonText';
        var dateTimeObj = assertPlainObject(scheduleTextObj.dateTimePlacePersonText, dateTimePath);
        assertRecord(dateTimeObj, dateTimePath, ['textZh']);
        var scheduleText = optionalString(dateTimeObj.textZh, dateTimePath + '.textZh', MAX_STRING);

        var stdCount = optionalInteger(obj.stdCount, path + '.stdCount', 1000000);

        var coursePath = path + '.course';
        var courseObj = assertPlainObject(obj.course, coursePath);
        assertRecord(courseObj, coursePath, [
            'id', 'nameZh', 'credits', 'code', 'periodInfo', 'courseType'
        ]);
        var courseId = requireNonNegativeInteger(
            courseObj.id, coursePath + '.id', Number.MAX_SAFE_INTEGER
        );
        var courseName = requireNonEmptyString(courseObj.nameZh, coursePath + '.nameZh', MAX_NAME);
        var credits = courseObj.credits === null || courseObj.credits === undefined
            ? null
            : requireFiniteNumber(courseObj.credits, coursePath + '.credits', 0, 1000);
        var courseCode = requireNonEmptyString(courseObj.code, coursePath + '.code', MAX_CODE);

        var periodInfoPath = coursePath + '.periodInfo';
        var periodInfoObj = assertPlainObject(courseObj.periodInfo, periodInfoPath);
        assertRecord(periodInfoObj, periodInfoPath, ['weeks']);
        var courseWeeks = optionalInteger(periodInfoObj.weeks, periodInfoPath + '.weeks', 1000);
        var courseType = multiLanguageName(courseObj.courseType, coursePath + '.courseType');

        var classCourseType = multiLanguageName(obj.courseType, path + '.courseType');
        var openDepartment = multiLanguageName(obj.openDepartment, path + '.openDepartment');
        var examMode = multiLanguageName(obj.examMode, path + '.examMode');
        var scheduleWeeksInfo = optionalString(
            obj.scheduleWeeksInfo, path + '.scheduleWeeksInfo', MAX_STRING
        );
        var planExamWeek = optionalInteger(obj.planExamWeek, path + '.planExamWeek', 1000);
        var teacherAssignments = validateTermTeacherAssignments(
            obj.teacherAssignmentList, path + '.teacherAssignmentList'
        );

        var semesterPath = path + '.semester';
        var semesterObj = assertPlainObject(obj.semester, semesterPath);
        assertRecord(semesterObj, semesterPath, ['id', 'nameZh', 'startDate', 'endDate']);
        var lessonSemesterId = requirePositiveInteger(
            semesterObj.id, semesterPath + '.id', Number.MAX_SAFE_INTEGER
        );
        if (lessonSemesterId !== semesterId) {
            fail(semesterPath + '.id', 'does not equal the caller-provided semesterId');
        }
        var semesterName = requireNonEmptyString(
            semesterObj.nameZh, semesterPath + '.nameZh', MAX_NAME
        );
        var start = parseISODate(semesterObj.startDate, semesterPath + '.startDate');
        var end = parseISODate(semesterObj.endDate, semesterPath + '.endDate');
        if (start.ms > end.ms) {
            fail(semesterPath, 'startDate must not be after endDate');
        }

        var classCode = requireNonEmptyString(obj.code, path + '.code', MAX_CODE);

        return {
            id: id,
            nameZh: nameZh,
            remark: remark,
            scheduleText: scheduleText,
            stdCount: stdCount,
            course: {
                id: courseId,
                nameZh: courseName,
                credits: credits,
                code: courseCode,
                weeks: courseWeeks,
                courseType: courseType
            },
            courseType: classCourseType,
            openDepartment: openDepartment,
            examMode: examMode,
            scheduleWeeksInfo: scheduleWeeksInfo,
            planExamWeek: planExamWeek,
            teacherAssignments: teacherAssignments,
            semester: {
                id: lessonSemesterId,
                nameZh: semesterName,
                startDate: start,
                endDate: end
            },
            code: classCode
        };
    }

    function validateTermData(raw, semesterId) {
        var obj = assertPlainObject(raw, 'input.termData');
        assertExactKeys(obj, TERM_KEYS, 'input.termData');

        var rawIds = requireArray(obj.lessonIds, 'input.termData.lessonIds', 1, MAX_LIST);
        var lessonIds = rawIds.map(function (value, index) {
            return requirePositiveInteger(
                value,
                'input.termData.lessonIds[' + index + ']',
                Number.MAX_SAFE_INTEGER
            );
        });
        var idSet = new Set();
        lessonIds.forEach(function (id, index) {
            if (idSet.has(id)) {
                fail('input.termData.lessonIds[' + index + ']', 'duplicate lesson id');
            }
            idSet.add(id);
        });

        var timeTableLayoutId = requirePositiveInteger(
            obj.timeTableLayoutId,
            'input.termData.timeTableLayoutId',
            Number.MAX_SAFE_INTEGER
        );

        var rawWeekIndices = requireArray(
            obj.weekIndices, 'input.termData.weekIndices', 1, 2000
        );
        var weekIndices = rawWeekIndices.map(function (value, index) {
            return requireNonNegativeInteger(
                value,
                'input.termData.weekIndices[' + index + ']',
                10000
            );
        });
        var weekSet = new Set();
        weekIndices.forEach(function (week, index) {
            if (weekSet.has(week)) {
                fail('input.termData.weekIndices[' + index + ']', 'duplicate week index');
            }
            weekSet.add(week);
        });

        var currentWeek = obj.currentWeek;
        if (typeof currentWeek !== 'number' || !Number.isSafeInteger(currentWeek) || Math.abs(currentWeek)>10000) fail('input.termData.currentWeek', 'invalid uninterpreted school index');

        var rawLessons = requireArray(obj.lessons, 'input.termData.lessons', 1, MAX_LIST);
        var lessons = [];
        var lessonById = new Map();
        var semesterMeta = null;

        rawLessons.forEach(function (rawLesson, index) {
            var path = 'input.termData.lessons[' + index + ']';
            var lesson = validateTermLesson(rawLesson, path, semesterId);
            if (lessonById.has(lesson.id)) {
                fail(path + '.id', 'duplicate lesson id');
            }
            if (!idSet.has(lesson.id)) {
                fail(path + '.id', 'is not present in termData.lessonIds');
            }
            lessonById.set(lesson.id, lesson);

            if (semesterMeta === null) {
                semesterMeta = lesson.semester;
            } else {
                if (lesson.semester.nameZh !== semesterMeta.nameZh ||
                    lesson.semester.startDate.text !== semesterMeta.startDate.text ||
                    lesson.semester.endDate.text !== semesterMeta.endDate.text) {
                    fail(path + '.semester',
                        'nameZh/startDate/endDate must be identical for every lesson');
                }
            }
            lessons.push(lesson);
        });

        if (lessons.length !== idSet.size) {
            fail('input.termData.lessons',
                'ids must be exactly the non-empty unique set in lessonIds');
        }
        idSet.forEach(function (id) {
            if (!lessonById.has(id)) {
                fail('input.termData.lessons', 'missing lesson id ' + id + ' from lessonIds');
            }
        });

        return {
            lessonIds: lessonIds.slice(),
            lessons: lessons,
            lessonById: lessonById,
            semester: semesterMeta,
            timeTableLayoutId: timeTableLayoutId,
            weekIndices: weekIndices,
            currentWeek: currentWeek
        };
    }

    function validateStudentId(value) {
        if (typeof value === 'string') {
            if (value.trim().length === 0) {
                fail('input.studentId', 'must be a non-empty string');
            }
            if (value.length > 64) {
                fail('input.studentId', 'must be at most 64 characters');
            }
            return value;
        }
        return requirePositiveInteger(value, 'input.studentId', Number.MAX_SAFE_INTEGER);
    }

    function validateLayoutData(raw) {
        var envelope = assertPlainObject(raw, 'input.layoutData');
        assertExactKeys(envelope, ['result'], 'input.layoutData');
        var result = assertPlainObject(envelope.result, 'input.layoutData.result');
        assertExactKeys(result, ['courseUnitList'], 'input.layoutData.result');
        var rawUnits = requireArray(
            result.courseUnitList, 'input.layoutData.result.courseUnitList', 1, 24
        );

        var units = rawUnits.map(function (rawUnit, index) {
            var path = 'input.layoutData.result.courseUnitList[' + index + ']';
            var obj = assertPlainObject(rawUnit, path);
            assertRecord(obj, path, [
                'nameZh', 'startTime', 'endTime', 'startTimeText', 'endTimeText'
            ]);
            var number = parseUnitNumber(obj.nameZh, path + '.nameZh');
            var start = parseClockInteger(obj.startTime, path + '.startTime');
            var end = parseClockInteger(obj.endTime, path + '.endTime');
            var startText = parseClockText(obj.startTimeText, path + '.startTimeText');
            var endText = parseClockText(obj.endTimeText, path + '.endTimeText');
            if (start.text !== startText.text) {
                fail(path + '.startTimeText', 'must equal the numeric startTime');
            }
            if (end.text !== endText.text) {
                fail(path + '.endTimeText', 'must equal the numeric endTime');
            }
            if (start.minuteOfDay >= end.minuteOfDay) {
                fail(path, 'startTime must be earlier than endTime');
            }
            return {
                number: number,
                startMinute: start.minuteOfDay,
                endMinute: end.minuteOfDay,
                startText: start.text,
                endText: end.text,
                path: path
            };
        });

        units.sort(function (a, b) {
            return a.number - b.number;
        });
        units.forEach(function (unit, index) {
            if (unit.number !== index + 1) {
                fail(unit.path + '.nameZh',
                    'section numbers must be unique and continuous from 1 to N (N <= 24)');
            }
            if (index > 0 && unit.startMinute < units[index - 1].endMinute) {
                fail(unit.path, 'section clock intervals must increase without overlapping');
            }
        });

        var startByText = new Map();
        var endByText = new Map();
        units.forEach(function (unit, index) {
            unit.index = index;
            if (startByText.has(unit.startText)) {
                fail(unit.path + '.startTime', 'duplicate section start time');
            }
            startByText.set(unit.startText, unit);
            if (endByText.has(unit.endText)) {
                fail(unit.path + '.endTime', 'duplicate section end time');
            }
            endByText.set(unit.endText, unit);
        });

        return {
            units: units,
            startByText: startByText,
            endByText: endByText
        };
    }

    function validateTableLesson(raw, path, term) {
        var obj = assertPlainObject(raw, path);
        assertRecord(obj, path, [
            'courseName', 'name', 'id', 'suggestScheduleWeekInfo',
            'courseTypeName', 'remark', 'teacherAssignmentList'
        ]);
        var idText = requireNonEmptyString(obj.id, path + '.id', 32);
        if (!/^[1-9]\d*$/.test(idText)) fail(path + '.id', 'must be a decimal teaching-class identifier');
        var id = requirePositiveInteger(Number(idText), path + '.id', Number.MAX_SAFE_INTEGER);
        var termLesson = term.lessonById.get(id);
        if (!termLesson) {
            fail(path + '.id', 'unknown teaching-class id');
        }
        var courseName = requireNonEmptyString(obj.courseName, path + '.courseName', MAX_NAME);
        if (courseName !== termLesson.course.nameZh) {
            fail(path + '.courseName',
                'contradicts termData lesson course.nameZh for lesson ' + id);
        }
        return {
            id: id,
            courseName: courseName,
            name: requireNonEmptyString(obj.name, path + '.name', MAX_NAME),
            suggestScheduleWeekInfo: requireString(
                obj.suggestScheduleWeekInfo, path + '.suggestScheduleWeekInfo', 512
            ),
            courseTypeName: requireString(
                obj.courseTypeName, path + '.courseTypeName', MAX_NAME
            ),
            remark: optionalString(obj.remark, path + '.remark', MAX_STRING),
            teacherAssignments: validateTableTeacherAssignments(
                obj.teacherAssignmentList, path + '.teacherAssignmentList'
            )
        };
    }

    function validateScheduleGroups(raw, term) {
        var list = requireArray(
            raw, 'input.datumData.result.scheduleGroupList', 0, MAX_LIST
        );
        var byId = new Map();
        list.forEach(function (rawGroup, index) {
            var path = 'input.datumData.result.scheduleGroupList[' + index + ']';
            var obj = assertPlainObject(rawGroup, path);
            assertRecord(obj, path, ['lessonId', 'stdCount']);
            var lessonId = requirePositiveInteger(
                obj.lessonId, path + '.lessonId', Number.MAX_SAFE_INTEGER
            );
            if (!term.lessonById.has(lessonId)) {
                fail(path + '.lessonId', 'unknown teaching-class id');
            }
            if (byId.has(lessonId)) {
                fail(path + '.lessonId', 'duplicate schedule group');
            }
            var stdCount = requirePositiveInteger(
                obj.stdCount, path + '.stdCount', 1000000
            );
            var lesson = term.lessonById.get(lessonId);
            if (lesson.stdCount !== null && stdCount !== lesson.stdCount) {
                fail(path + '.stdCount',
                    'contradicts termData lesson stdCount for lesson ' + lessonId);
            }
            byId.set(lessonId, { lessonId: lessonId, stdCount: stdCount });
        });
        return byId;
    }

    function validateScheduleList(raw, term, layout, semester, week1, totalWeeks) {
        var list = requireArray(
            raw, 'input.datumData.result.scheduleList', 0, MAX_LIST
        );
        var occurrences = [];
        var exactKeys = new Set();
        var slotClaims = new Map();
        var intervals = new Map();

        list.forEach(function (rawSchedule, index) {
            var path = 'input.datumData.result.scheduleList[' + index + ']';
            var obj = assertPlainObject(rawSchedule, path);
            assertRecord(obj, path, [
                'lessonId', 'room', 'weekday', 'personName', 'weekIndex',
                'startTime', 'periods', 'endTime', 'date', 'lessonType'
            ]);

            var lessonId = requirePositiveInteger(
                obj.lessonId, path + '.lessonId', Number.MAX_SAFE_INTEGER
            );
            if (!term.lessonById.has(lessonId)) {
                fail(path + '.lessonId', 'unknown teaching-class id');
            }

            var date = parseISODate(obj.date, path + '.date');
            if (date.ms < semester.startDate.ms || date.ms > semester.endDate.ms) {
                fail(path + '.date', 'is outside the semester startDate/endDate');
            }

            var weekday = requirePositiveInteger(obj.weekday, path + '.weekday', 7);
            if (weekday !== date.weekday) {
                fail(path + '.weekday',
                    'contradicts the actual weekday (Monday=1 .. Sunday=7) of the date');
            }

            var diffDays = Math.round((date.ms - week1.ms) / DAY_MS);
            var week = Math.floor(diffDays / 7) + 1;
            if (week < 1 || week > totalWeeks) {
                fail(path + '.date',
                    'computed week ' + week + ' is outside 1..' + totalWeeks);
            }

            var weekIndex = requireNonNegativeInteger(
                obj.weekIndex, path + '.weekIndex', 10000
            );

            var start = parseClockInteger(obj.startTime, path + '.startTime');
            var end = parseClockInteger(obj.endTime, path + '.endTime');
            if (start.minuteOfDay >= end.minuteOfDay) {
                fail(path, 'startTime must be earlier than endTime; overnight blocks are not supported');
            }

            var periods = requirePositiveInteger(
                obj.periods, path + '.periods', layout.units.length
            );
            var startUnit = layout.startByText.get(start.text);
            if (!startUnit) {
                fail(path + '.startTime',
                    'must exactly equal a section start time; nearest-time guessing is not allowed');
            }
            var endIndex = startUnit.index + periods - 1;
            if (endIndex >= layout.units.length) {
                fail(path + '.periods', 'extends beyond the last declared section');
            }
            var endUnit = layout.units[endIndex];
            if (endUnit.endText !== end.text) {
                fail(path + '.endTime',
                    'must exactly equal the end of the last section implied by periods');
            }
            if (endUnit.number !== startUnit.number + periods - 1) {
                fail(path + '.periods', 'does not describe a continuous section span');
            }

            var roomPath = path + '.room';
            var roomObj = assertPlainObject(obj.room, roomPath);
            assertRecord(roomObj, roomPath, ['nameZh']);
            var room = requireNonEmptyString(roomObj.nameZh, roomPath + '.nameZh', MAX_NAME);
            var teacher = requireNonEmptyString(obj.personName, path + '.personName', MAX_NAME);
            var lessonType = requireString(obj.lessonType, path + '.lessonType', MAX_CODE);

            var exactKey = JSON.stringify([
                lessonId, date.text, weekday, weekIndex, start.text, end.text,
                periods, teacher, room, lessonType
            ]);
            if (exactKeys.has(exactKey)) {
                fail(path, 'duplicate schedule record');
            }
            exactKeys.add(exactKey);

            var slotKey = lessonId + '|' + date.text + '|' + start.text + '|' + end.text;
            if (slotClaims.has(slotKey)) {
                fail(path, 'conflicting duplicate for the same class, date, and time slot');
            }
            slotClaims.set(slotKey, true);

            var intervalKey = lessonId + '|' + date.text;
            var dayIntervals = intervals.get(intervalKey);
            if (!dayIntervals) {
                dayIntervals = [];
                intervals.set(intervalKey, dayIntervals);
            }
            dayIntervals.forEach(function (existing) {
                if (start.minuteOfDay < existing.endMinute &&
                    existing.startMinute < end.minuteOfDay) {
                    fail(path,
                        'overlaps another schedule of the same class on the same date');
                }
            });
            dayIntervals.push({
                startMinute: start.minuteOfDay,
                endMinute: end.minuteOfDay
            });

            occurrences.push({
                index: index,
                path: path,
                lessonId: lessonId,
                date: date.text,
                weekday: weekday,
                week: week,
                weekIndex: weekIndex,
                startText: start.text,
                endText: end.text,
                startMinute: start.minuteOfDay,
                endMinute: end.minuteOfDay,
                periods: periods,
                startSection: startUnit.number,
                endSection: endUnit.number,
                teacher: teacher,
                room: room,
                lessonType: lessonType
            });
        });

        var covered = new Set();
        occurrences.forEach(function (occurrence) {
            covered.add(occurrence.lessonId);
        });
        term.lessonIds.forEach(function (id) {
            if (!covered.has(id)) {
                fail('input.datumData.result.scheduleList',
                    'no schedule exists for teaching-class id ' + id);
            }
        });

        return occurrences;
    }

    function validateDatumData(raw, term, layout, week1, totalWeeks) {
        var envelope = assertPlainObject(raw, 'input.datumData');
        assertExactKeys(envelope, ['result'], 'input.datumData');
        var result = assertPlainObject(envelope.result, 'input.datumData.result');
        assertExactKeys(result, TABLE_RESULT_KEYS, 'input.datumData.result');

        var rawLessonList = requireArray(
            result.lessonList, 'input.datumData.result.lessonList', 0, MAX_LIST
        );
        var tableById = new Map();
        rawLessonList.forEach(function (rawTableLesson, index) {
            var path = 'input.datumData.result.lessonList[' + index + ']';
            var tableLesson = validateTableLesson(rawTableLesson, path, term);
            if (tableById.has(tableLesson.id)) {
                fail(path + '.id', 'duplicate lesson id');
            }
            tableById.set(tableLesson.id, tableLesson);
        });

        if (tableById.size !== term.lessonIds.length) {
            fail('input.datumData.result.lessonList',
                'must cover exactly every termData lesson id');
        }
        term.lessonIds.forEach(function (id) {
            if (!tableById.has(id)) {
                fail('input.datumData.result.lessonList', 'missing lesson id ' + id);
            }
        });

        var groupById = validateScheduleGroups(result.scheduleGroupList, term);
        var occurrences = validateScheduleList(
            result.scheduleList, term, layout, term.semester, week1, totalWeeks
        );

        return {
            tableById: tableById,
            groupById: groupById,
            occurrences: occurrences
        };
    }

    function cloneData(value) {
        if (Array.isArray(value)) {
            return value.map(cloneData);
        }
        if (isPlainObject(value)) {
            var copy = {};
            Object.keys(value).forEach(function (key) {
                copy[key] = cloneData(value[key]);
            });
            return copy;
        }
        return value;
    }

    function buildBaseDetails(lesson, tableLesson, group) {
        return {
            lessonId: lesson.id,
            classCode: lesson.code,
            className: lesson.nameZh,
            courseId: lesson.course.id,
            courseCode: lesson.course.code,
            credits: lesson.course.credits,
            courseType: lesson.course.courseType,
            classCourseType: lesson.courseType,
            openDepartment: lesson.openDepartment,
            examMode: lesson.examMode,
            remark: lesson.remark,
            courseWeeks: lesson.course.weeks,
            scheduleText: lesson.scheduleText,
            scheduleWeeksInfo: lesson.scheduleWeeksInfo,
            planExamWeek: lesson.planExamWeek,
            stdCount: lesson.stdCount,
            groupStdCount: group ? group.stdCount : null,
            teacherAssignments: lesson.teacherAssignments,
            table: {
                courseName: tableLesson.courseName,
                className: tableLesson.name,
                courseTypeName: tableLesson.courseTypeName,
                remark: tableLesson.remark,
                suggestScheduleWeekInfo: tableLesson.suggestScheduleWeekInfo,
                teacherAssignments: tableLesson.teacherAssignments
            }
        };
    }

    function buildCourses(occurrences, term, tableById, groupById) {
        var groupsByKey = new Map();

        occurrences.forEach(function (occurrence) {
            var lesson = term.lessonById.get(occurrence.lessonId);
            var tableLesson = tableById.get(occurrence.lessonId);
            var group = groupById.get(occurrence.lessonId);
            var baseDetails = buildBaseDetails(lesson, tableLesson, group);
            var mergeKey = JSON.stringify([
                baseDetails,
                lesson.course.nameZh,
                occurrence.teacher,
                occurrence.room,
                occurrence.weekday,
                occurrence.startSection,
                occurrence.endSection,
                occurrence.startText,
                occurrence.endText,
                occurrence.periods,
                occurrence.lessonType
            ]);

            var merged = groupsByKey.get(mergeKey);
            if (!merged) {
                merged = {
                    baseDetails: baseDetails,
                    name: lesson.course.nameZh,
                    teacher: occurrence.teacher,
                    room: occurrence.room,
                    day: occurrence.weekday,
                    startSection: occurrence.startSection,
                    endSection: occurrence.endSection,
                    weekDates: new Map(),
                    occurrences: []
                };
                groupsByKey.set(mergeKey, merged);
            }

            if (merged.weekDates.has(occurrence.week)) {
                fail(occurrence.path,
                    'the same complete class placement occurs twice in actual week ' +
                    occurrence.week + '; duplicate-week contradictions may not be merged');
            }
            merged.weekDates.set(occurrence.week, occurrence.date);
            merged.occurrences.push(occurrence);
        });

        var maxWeek = 0;
        var courses = [];

        groupsByKey.forEach(function (merged) {
            merged.occurrences.sort(function (a, b) {
                if (a.date !== b.date) {
                    return a.date < b.date ? -1 : 1;
                }
                if (a.startText !== b.startText) {
                    return a.startText < b.startText ? -1 : 1;
                }
                return a.weekIndex - b.weekIndex;
            });

            var weeks = Array.from(merged.weekDates.keys()).sort(function (a, b) {
                return a - b;
            });
            weeks.forEach(function (week) {
                if (week > maxWeek) {
                    maxWeek = week;
                }
            });

            var details = cloneData(merged.baseDetails);
            details.occurrences = merged.occurrences.map(function (occurrence) {
                return {
                    date: occurrence.date,
                    week: occurrence.week,
                    weekday: occurrence.weekday,
                    schoolWeekIndex: occurrence.weekIndex,
                    teacher: occurrence.teacher,
                    room: occurrence.room,
                    startTime: occurrence.startText,
                    endTime: occurrence.endText,
                    periods: occurrence.periods,
                    startSection: occurrence.startSection,
                    endSection: occurrence.endSection,
                    lessonType: occurrence.lessonType
                };
            });
            details.dates = merged.occurrences.map(function (occurrence) {
                return occurrence.date;
            });
            details.schoolWeekIndices = merged.occurrences.map(function (occurrence) {
                return occurrence.weekIndex;
            });

            courses.push({
                name: merged.name,
                teacher: merged.teacher,
                room: merged.room,
                day: merged.day,
                startSection: merged.startSection,
                endSection: merged.endSection,
                weeks: weeks,
                details: details
            });
        });

        courses.sort(function (a, b) {
            if (a.details.lessonId !== b.details.lessonId) {
                return a.details.lessonId - b.details.lessonId;
            }
            if (a.day !== b.day) {
                return a.day - b.day;
            }
            if (a.startSection !== b.startSection) {
                return a.startSection - b.startSection;
            }
            if (a.endSection !== b.endSection) {
                return a.endSection - b.endSection;
            }
            if (a.teacher !== b.teacher) {
                return a.teacher < b.teacher ? -1 : 1;
            }
            if (a.room !== b.room) {
                return a.room < b.room ? -1 : 1;
            }
            return 0;
        });

        return { courses: courses, maxWeek: maxWeek };
    }

    function normalizeHFUT(input) {
        var root = assertPlainObject(input, 'input');
        assertExactKeys(root, INPUT_KEYS, 'input');
        assertStringBounds(root, 'input', new Set());

        validateStudentId(root.studentId);
        var semesterId = requirePositiveInteger(
            root.semesterId, 'input.semesterId', Number.MAX_SAFE_INTEGER
        );
        var totalWeeks = requirePositiveInteger(
            root.totalWeeks, 'input.totalWeeks', MAX_WEEKS
        );
        var week1 = parseISODate(root.week1Monday, 'input.week1Monday');
        if (week1.weekday !== 1) {
            fail('input.week1Monday', 'must be a real Monday');
        }

        var term = validateTermData(root.termData, semesterId);
        if (Math.abs(week1.ms-term.semester.startDate.ms)>6*DAY_MS) fail('input.week1Monday', 'must be within six days of the actual semester start');
        var layout = validateLayoutData(root.layoutData);
        var datum = validateDatumData(
            root.datumData, term, layout, week1, totalWeeks
        );
        var built = buildCourses(
            datum.occurrences, term, datum.tableById, datum.groupById
        );

        var times = layout.units.map(function (unit) {
            return {
                number: unit.number,
                startTime: unit.startText,
                endTime: unit.endText
            };
        });

        return {
            term: {
                id: term.semester.id,
                name: term.semester.nameZh,
                start: term.semester.startDate.text,
                end: term.semester.endDate.text
            },
            times: times,
            courses: built.courses,
            maxWeek: built.maxWeek
        };
    }

    normalizeHFUT.HFUTNormalizeError = HFUTNormalizeError;

    if (typeof module === 'object' && module !== null && module.exports) {
        module.exports = normalizeHFUT;
    } else {
        global.normalizeHFUT = normalizeHFUT;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this));

// KAgenda's local host draft. Public protocol references: Chiu-xaH/HFUT-Schedule
// (Apache-2.0) and Junpgle/HFUTCampusNet (MIT). 
(function () {
  'use strict';
  if (typeof window === 'undefined' || !window.kagendaSchool) return;
  const k = window.kagendaSchool;
  const base = '/eams5-student/';
  const need = (ok, message) => k.requireValue(ok, message);
  const stable = value => JSON.stringify(canonical(value));
  function canonical(value) {
    if (Array.isArray(value)) return value.map(canonical);
    if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
    return value;
  }
  function page() {
    const url = new URL(location.href);
    need(k.adapterId === 'ext_hfut_personal' && ['http:', 'https:'].includes(url.protocol) &&
      url.hostname === 'jxglstu.hfut.edu.cn' && !url.username && !url.password && !url.port,
      '请登录合工大教务并打开个人完整课表');
    const path = url.pathname.match(/^\/eams5-student\/for-std\/course-table\/info\/([1-9]\d*)$/);
    need(path && Number.isSafeInteger(Number(path[1])), '请打开本人完整课表页面');
    need(document.querySelectorAll('iframe').length === 0, '请先打开课表正文，不支持未展开的框架');
    const text = Array.from(document.querySelectorAll('script:not([src])')).map(s => s.textContent || '').join('\n');
    const literal = (pattern, label) => {
      const values = [...new Set(Array.from(text.matchAll(pattern), m => m[1]))];
      need(values.length === 1 && Number.isSafeInteger(Number(values[0])) && Number(values[0]) > 0,
        '页面没有唯一明确的' + label + '，不能填写默认值');
      return values[0];
    };
    const studentId = literal(/\bvar\s+studentId\s*=\s*([1-9]\d*)\b/g, '本人课表标识');
    const bizTypeId = literal(/\bbizTypeId\s*:\s*([1-9]\d*)\b/g, '培养类型');
    const semesterId = literal(/\bsemesterId\s*:\s*([1-9]\d*)\b/g, '学期标识');
    need(studentId === path[1], '页面身份与本人课表地址不一致');
    const expected = {studentId, dataId: studentId, bizTypeId, semesterId};
    need(!url.hash, '当前页面有未核实的局部导航或筛选');
    const queryKeys = new Set();
    for (const [name, value] of url.searchParams) {
      need(!queryKeys.has(name) && Object.hasOwn(expected, name) && value === expected[name], '地址含未知或冲突的学期、身份或筛选');
      queryKeys.add(name);
    }
    const controls = [];
    for (const node of document.querySelectorAll('input,select,textarea')) {
      const type = String(node.type || '').toLowerCase(), name = node.name || node.id || '';
      need(!['password', 'file', 'checkbox', 'radio'].includes(type), '页面含登录输入或未知课程开关');
      if (['button', 'submit', 'reset'].includes(type)) continue;
      if (/^(?:csrf|csrfToken|_csrf|__RequestVerificationToken)$/i.test(name)) continue;
      const value = String(node.value || '').trim();
      const label = node.tagName === 'SELECT' ? String(node.selectedOptions?.[0]?.textContent || '').trim() : '';
      if (node.tagName === 'SELECT') need(!node.disabled && !node.multiple && node.selectedOptions.length === 1 &&
        !node.selectedOptions[0].disabled, '学期或筛选控件不完整');
      const explicitTerm = node.tagName === 'SELECT' && /^(?:semesterId|semester|semesterAssoc|allSemesters)$/.test(name) &&
        value === semesterId && /\d{4}.*(?:学年|学期|季|春|秋|\d{4})/.test(label);
      need(explicitTerm || (Object.hasOwn(expected, name) && value === expected[name]) ||
        (!value && !label) || (node.tagName === 'SELECT' && /^(全部|所有|不限|全学期|全部周次)$/.test(label)),
        '存在未知学期、周次、课程或校区筛选，不能视为全量课表');
      controls.push([name, value, label]);
    }
    const body = String(document.body?.innerText || '');
    need(!/下一页|上一页|未排课|未安排时间|未安排课表/.test(body), '页面有分页或未排课程，需要先核实完整性');
    return {href: url.href, studentId, bizTypeId, semesterId, controls};
  }
  async function request(path, body) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    const url = new URL(base + path, location.href);
    try {
      const response = await fetch(url.href, {method: body == null ? 'GET' : 'POST',
        headers: body == null ? {'Accept': 'application/json'} : {'Accept': 'application/json', 'Content-Type': 'application/json'},
        ...(body == null ? {} : {body: JSON.stringify(body)}), credentials: 'same-origin',
        redirect: 'error', cache: 'no-store', signal: controller.signal});
      need(response.ok && response.status === 200 && !response.redirected, '学校会话失效或请求未完整成功');
      if (response.url) {
        const actual = new URL(response.url);
        need(actual.origin === url.origin && actual.pathname === url.pathname, '课表响应地址变化，请重新登录');
      }
      const text = await response.text();
      need(text.length > 0 && text.length <= 2500000 && text.trim().startsWith('{'), '学校未返回完整JSON，可能需要重新登录');
      return JSON.parse(text);
    } finally { clearTimeout(timer); }
  }
  async function fetchAll(state) {
    const query = new URLSearchParams({bizTypeId: state.bizTypeId, semesterId: state.semesterId, dataId: state.studentId});
    const termData = await request('for-std/course-table/get-data?' + query);
    need(Array.isArray(termData.lessonIds) && termData.lessonIds.length > 0 && termData.lessonIds.length <= 5000 &&
      termData.lessonIds.every(id => typeof id === 'number' && Number.isSafeInteger(id) && id > 0), '课程标识列表不完整');
    need(Number.isSafeInteger(termData.timeTableLayoutId) && termData.timeTableLayoutId > 0, '学校没有明确的作息表标识');
    const datumData = await request('ws/schedule-table/datum', {lessonIds: termData.lessonIds, studentId: Number(state.studentId), weekIndex: ''});
    const layoutData = await request('ws/schedule-table/timetable-layout', {timeTableLayoutId: termData.timeTableLayoutId});
    return {termData, datumData, layoutData};
  }
  k.run(async () => {
    const initial = page(), mark = stable(initial);
    const guard = () => need(stable(page()) === mark, '确认期间身份、学期、筛选或页面地址变化');
    const first = await fetchAll(initial); guard();
    need(Array.isArray(first.termData.lessons) && first.termData.lessons.length > 0, '学校没有完整教学班列表');
    const terms = first.termData.lessons.map(v => v?.semester);
    need(terms.every(v => v && String(v.id) === initial.semesterId && typeof v.nameZh === 'string' && v.nameZh.trim() &&
      typeof v.startDate === 'string' && typeof v.endDate === 'string' && stable(v) === stable(terms[0])), '课程所属学期不完整或不一致');
    const term = terms[0];
    const monday = await k.api.showPrompt('第1教学周周一', term.nameZh + '（' + term.startDate + ' 至 ' + term.endDate +
      '）。请按学校校历填写第1教学周周一，格式 YYYY-MM-DD；不使用默认值。', '', 1);
    need(monday != null && typeof monday === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(monday), '未明确填写周一，已停止导入'); guard();
    const rawCount = await k.api.showPrompt('学期总周数', '请按所选学期校历明确填写总教学周数（1-40），不以最后一门课程猜测。', '', 1);
    need(typeof rawCount === 'string' && /^\d{1,2}$/.test(rawCount), '未明确填写学期周数，已停止导入');
    const totalWeeks = k.integer(rawCount, 1, 40, '学期总周数'); guard();
    const final = await fetchAll(initial); guard();
    need(stable(first) === stable(final), '两次完整课表、学期或作息不一致，请重新导入');
    const normalized = window.normalizeHFUT({...final, semesterId: Number(initial.semesterId), studentId: initial.studentId,
      week1Monday: monday, totalWeeks});
    need(Array.isArray(normalized.courses) && normalized.courses.length > 0 && normalized.courses.length <= 5000, '没有完整课程可导入');
    const courses = normalized.courses.map(c => {
      const value = k.course(c.name, c.teacher, c.room, c.day, c.startSection, c.endSection, c.weeks);
      const d = c.details;
      need(d && typeof d === 'object' && !Array.isArray(d), '课程详情不完整');
      const blocks = [];
      const add = (label, text) => { if (text != null && String(text).trim()) blocks.push(label + '：' + String(text).trim()); };
      add('教学班', d.classCode); add('教学班标识', d.lessonId); add('教学班名称', d.className);
      add('课程代码', d.courseCode); add('学分', d.credits);
      add('课程性质', [...new Set([d.courseType, d.classCourseType, d.table?.courseTypeName].filter(Boolean))].join(' / '));
      add('开课单位', d.openDepartment); add('考核方式', d.examMode);
      add('课程备注', [...new Set([d.remark, d.table?.remark].filter(Boolean))].join('\n'));
      add('学校排课说明', d.scheduleText); add('学校周次说明', d.scheduleWeeksInfo);
      add('教学班教师', [...new Set([...(d.teacherAssignments || []).map(t => t.personName),
        ...(d.table?.teacherAssignments || []).map(t => t.name)].filter(Boolean))].join('、'));
      add('实际课次日期', d.dates?.join('、')); add('学校原始周索引', d.schoolWeekIndices?.join('、'));
      add('学期', normalized.term.name);
      return Object.assign(value, {extraInfo: blocks.join('\n')});
    });
    need(courses.every(c => c.extraInfo.length <= 4000), '课程详情过长');
    const saved = await k.api.savePresetTimeSlots(JSON.stringify(normalized.times));
    need(saved !== false, '作息暂存失败');
    await k.finish(courses, 0, totalWeeks, monday);
  });
})();
