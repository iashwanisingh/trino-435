/*
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
package io.prestosql.spi.type;

import io.airlift.slice.XxHash64;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.block.BlockBuilderStatus;
import io.prestosql.spi.block.Int96ArrayBlockBuilder;
import io.prestosql.spi.block.PageBuilderStatus;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.function.BlockIndex;
import io.prestosql.spi.function.BlockPosition;
import io.prestosql.spi.function.ScalarOperator;

import static io.airlift.slice.SizeOf.SIZE_OF_LONG;
import static io.prestosql.spi.function.OperatorType.EQUAL;
import static io.prestosql.spi.function.OperatorType.HASH_CODE;
import static io.prestosql.spi.function.OperatorType.XX_HASH_64;
import static io.prestosql.spi.type.TimeWithTimezoneTypes.normalizePicos;
import static io.prestosql.spi.type.TypeOperatorDeclaration.extractOperatorDeclaration;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;

class LongTimeWithTimeZoneType
        extends TimeWithTimeZoneType
{
    private static final TypeOperatorDeclaration TYPE_OPERATOR_DECLARATION = extractOperatorDeclaration(LongTimeWithTimeZoneType.class, lookup(), LongTimeWithTimeZone.class);

    public LongTimeWithTimeZoneType(int precision)
    {
        super(precision, LongTimeWithTimeZone.class);

        if (precision < MAX_SHORT_PRECISION + 1 || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(format("Precision must be in the range [%s, %s]", MAX_SHORT_PRECISION + 1, MAX_PRECISION));
        }
    }

    @Override
    public TypeOperatorDeclaration getTypeOperatorDeclaration(TypeOperators typeOperators)
    {
        return TYPE_OPERATOR_DECLARATION;
    }

    @Override
    public int getFixedSize()
    {
        return Long.BYTES + Integer.BYTES;
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries, int expectedBytesPerEntry)
    {
        int maxBlockSizeInBytes;
        if (blockBuilderStatus == null) {
            maxBlockSizeInBytes = PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES;
        }
        else {
            maxBlockSizeInBytes = blockBuilderStatus.getMaxPageSizeInBytes();
        }
        return new Int96ArrayBlockBuilder(
                blockBuilderStatus,
                Math.min(expectedEntries, maxBlockSizeInBytes / getFixedSize()));
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries)
    {
        return createBlockBuilder(blockBuilderStatus, expectedEntries, getFixedSize());
    }

    @Override
    public BlockBuilder createFixedSizeBlockBuilder(int positionCount)
    {
        return new Int96ArrayBlockBuilder(null, positionCount);
    }

    @Override
    public boolean equalTo(Block leftBlock, int leftPosition, Block rightBlock, int rightPosition)
    {
        return equalOperator(leftBlock, leftPosition, rightBlock, rightPosition);
    }

    @Override
    public int compareTo(Block leftBlock, int leftPosition, Block rightBlock, int rightPosition)
    {
        long leftPicos = getPicos(leftBlock, leftPosition);
        int leftOffsetMinutes = getOffsetMinutes(leftBlock, leftPosition);

        long rightPicos = getPicos(rightBlock, rightPosition);
        int rightOffsetMinutes = getOffsetMinutes(rightBlock, leftPosition);

        return Long.compare(normalizePicos(leftPicos, leftOffsetMinutes), normalizePicos(rightPicos, rightOffsetMinutes));
    }

    @Override
    public long hash(Block block, int position)
    {
        return hashCodeOperator(block, position);
    }

    @Override
    public void appendTo(Block block, int position, BlockBuilder blockBuilder)
    {
        if (block.isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            blockBuilder.writeLong(getPicos(block, position));
            blockBuilder.writeInt(getOffsetMinutes(block, position));
            blockBuilder.closeEntry();
        }
    }

    @Override
    public Object getObject(Block block, int position)
    {
        return new LongTimeWithTimeZone(getPicos(block, position), getOffsetMinutes(block, position));
    }

    @Override
    public void writeObject(BlockBuilder blockBuilder, Object value)
    {
        LongTimeWithTimeZone timestamp = (LongTimeWithTimeZone) value;
        blockBuilder.writeLong(timestamp.getPicoSeconds());
        blockBuilder.writeInt(timestamp.getOffsetMinutes());
        blockBuilder.closeEntry();
    }

    @Override
    public Object getObjectValue(ConnectorSession session, Block block, int position)
    {
        if (block.isNull(position)) {
            return null;
        }

        return SqlTimeWithTimeZone.newInstance(getPrecision(), getPicos(block, position), getOffsetMinutes(block, position));
    }

    private static long getPicos(Block block, int position)
    {
        return block.getLong(position, 0);
    }

    private static int getOffsetMinutes(Block block, int position)
    {
        return block.getInt(position, SIZE_OF_LONG);
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(LongTimeWithTimeZone left, LongTimeWithTimeZone right)
    {
        return equal(
                left.getPicoSeconds(),
                left.getOffsetMinutes(),
                right.getPicoSeconds(),
                right.getOffsetMinutes());
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return equal(
                getPicos(leftBlock, leftPosition),
                getOffsetMinutes(leftBlock, leftPosition),
                getPicos(rightBlock, rightPosition),
                getOffsetMinutes(rightBlock, rightPosition));
    }

    private static boolean equal(long leftPicos, int leftOffsetMinutes, long rightPicos, int rightOffsetMinutes)
    {
        return normalizePicos(leftPicos, leftOffsetMinutes) == normalizePicos(rightPicos, rightOffsetMinutes);
    }

    @ScalarOperator(HASH_CODE)
    private static long hashCodeOperator(LongTimeWithTimeZone value)
    {
        return hashCodeOperator(value.getPicoSeconds(), value.getOffsetMinutes());
    }

    @ScalarOperator(HASH_CODE)
    private static long hashCodeOperator(@BlockPosition Block block, @BlockIndex int position)
    {
        return hashCodeOperator(getPicos(block, position), getOffsetMinutes(block, position));
    }

    private static long hashCodeOperator(long picos, int offsetMinutes)
    {
        return AbstractLongType.hash(normalizePicos(picos, offsetMinutes));
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(LongTimeWithTimeZone value)
    {
        return xxHash64(value.getPicoSeconds(), value.getOffsetMinutes());
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(@BlockPosition Block block, @BlockIndex int position)
    {
        return xxHash64(getPicos(block, position), getOffsetMinutes(block, position));
    }

    private static long xxHash64(long picos, int offsetMinutes)
    {
        return XxHash64.hash(normalizePicos(picos, offsetMinutes));
    }
}
