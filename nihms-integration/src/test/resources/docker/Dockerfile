FROM stilliard/pure-ftpd

#
# docker run -e PUBLICHOST=0.0.0.0 -p 21:21 <image sha>
#

RUN echo \
    'nihmsftpuser:$6$x0HwRJld$xpRkxTjkstqg5.piEkUGcKyQGamvErjfNQKJiQZwdc18rSx/b.61u1TS9DF3ClMgrS7n3jXCALAq.v4CwXxGw.:500:101::/home/ftpusers/nihmsftpuser/./::::::1000:::::::' \
    > /etc/pureftpd.passwd

RUN echo 'yes' > /etc/pure-ftpd/conf/DontResolve

RUN echo 'yes' > /etc/pure-ftpd/conf/VerboseLog

RUN pure-pw mkdb /etc/pure-ftpd/pureftpd.pdb -f /etc/pureftpd.passwd

CMD /etc/init.d/rsyslog start && /run.sh -d -d -c 1 -C 1 -H -l puredb:/etc/pure-ftpd/pureftpd.pdb -E -j -R -p 30000:30010
