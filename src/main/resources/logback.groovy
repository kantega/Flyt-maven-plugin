/*
 * Copyright 2013 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

scan('5 seconds')
appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = '%d{HH:mm:ss} %-5level %logger{36} - %msg%n'
    }
}
logger('ro.isdc.wro', WARN)
logger('org.eclipse.jetty', WARN)
logger('org.springframework', ERROR)
logger('org.apache', ERROR)
logger('no.kantega.commons.filter', INFO)
root(DEBUG, ['STDOUT'])